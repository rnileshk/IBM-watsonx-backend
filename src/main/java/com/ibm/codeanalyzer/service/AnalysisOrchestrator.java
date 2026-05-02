package com.ibm.codeanalyzer.service;

import com.ibm.codeanalyzer.model.*;
import com.ibm.codeanalyzer.model.AnalysisResult.Status;
import com.ibm.codeanalyzer.model.Finding.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Analysis Orchestrator
 *
 * Coordinates the three analysis passes (security, quality, LLM) in parallel
 * using a CompletableFuture fan-out, then aggregates results into a single
 * AnalysisResult report.
 *
 * Job state is held in-memory (ConcurrentHashMap). For production, swap this
 * out for a Redis or database-backed store.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final IngestionService     ingestionService;
    private final SecurityScanService  securityScanService;
    private final QualityAnalysisService qualityAnalysisService;
    private final LLMAnalysisService   llmAnalysisService;

    // In-memory job store
    private final ConcurrentHashMap<String, AnalysisResult> jobStore = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────

    /** Runs analysis asynchronously. Job status can be polled via getResult(). */
    @Async("analysisExecutor")
    public void runAsync(
            AnalysisRequest request,
            Map<String, String> sources,
            String jobId) {

        // Register RUNNING state
        AnalysisResult running = AnalysisResult.builder()
                .jobId(jobId)
                .status(Status.RUNNING)
                .projectName(Optional.ofNullable(request.getProjectName()).orElse("Unnamed Project"))
                .startedAt(Instant.now())
                .build();
        jobStore.put(jobId, running);

        try {
            log.info("[{}] Starting parallel analysis on {} files", jobId, sources.size());

            // ── Parallel fan-out ───────────────────────────────────────
            CompletableFuture<List<Finding>> securityFuture =
                    CompletableFuture.supplyAsync(() -> securityScanService.scan(sources));

            CompletableFuture<List<Finding>> qualityFuture =
                    CompletableFuture.supplyAsync(() -> qualityAnalysisService.analyze(sources));

            CompletableFuture<LLMAnalysisService.LLMAnalysisOutput> llmFuture =
                    request.isIncludeLlmAnalysis()
                            ? CompletableFuture.supplyAsync(() -> {
                                List<CodeChunk> chunks = ingestionService.chunkSources(sources);
                                return llmAnalysisService.analyze(chunks, request.getProjectName());
                              })
                            : CompletableFuture.completedFuture(
                                new LLMAnalysisService.LLMAnalysisOutput(
                                    List.of(), "LLM analysis skipped", "", List.of()));

            // ── Wait for all passes ────────────────────────────────────
            CompletableFuture.allOf(securityFuture, qualityFuture, llmFuture).join();

            List<Finding> securityFindings     = securityFuture.get();
            List<Finding> qualityFindings      = qualityFuture.get();
            LLMAnalysisService.LLMAnalysisOutput llmOutput = llmFuture.get();

            // ── Build report ───────────────────────────────────────────
            AnalysisResult result = buildReport(
                    jobId, request, sources,
                    securityFindings, qualityFindings, llmOutput
            );

            jobStore.put(jobId, result);
            log.info("[{}] Analysis complete — {} total findings", jobId, result.getTotalFindings());

        } catch (Exception e) {
            log.error("[{}] Analysis failed: {}", jobId, e.getMessage(), e);
            AnalysisResult failed = AnalysisResult.builder()
                    .jobId(jobId)
                    .status(Status.FAILED)
                    .startedAt(running.getStartedAt())
                    .completedAt(Instant.now())
                    .errorMessage(e.getMessage())
                    .build();
            jobStore.put(jobId, failed);
        } finally {
            ingestionService.cleanup(jobId);
        }
    }

    /** Poll job status by ID */
    public Optional<AnalysisResult> getResult(String jobId) {
        return Optional.ofNullable(jobStore.get(jobId));
    }

    /** List all known job IDs */
    public List<String> listJobs() {
        return List.copyOf(jobStore.keySet());
    }

    /** Cleanup old completed jobs (runs every hour) */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldJobs() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        int removed = 0;
        
        Iterator<Map.Entry<String, AnalysisResult>> iterator = jobStore.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AnalysisResult> entry = iterator.next();
            AnalysisResult result = entry.getValue();
            
            if (result.getCompletedAt() != null && result.getCompletedAt().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} old jobs from memory", removed);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Report builder
    // ─────────────────────────────────────────────────────────────────

    private AnalysisResult buildReport(
            String jobId,
            AnalysisRequest request,
            Map<String, String> sources,
            List<Finding> securityFindings,
            List<Finding> qualityFindings,
            LLMAnalysisService.LLMAnalysisOutput llmOutput) {

        List<Finding> allFindings = new ArrayList<>();
        allFindings.addAll(securityFindings);
        allFindings.addAll(qualityFindings);
        allFindings.addAll(llmOutput.findings());

        // Count severity distribution
        Map<Severity, Long> bySeverity = allFindings.stream()
                .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));

        // Count LOC and language distribution
        int totalLoc = sources.values().stream()
                .mapToInt(content -> content.split("\n").length).sum();

        Map<String, Integer> byLanguage = new LinkedHashMap<>();
        sources.forEach((path, content) -> {
            String lang = ingestionService.detectLanguage(path);
            byLanguage.merge(lang, 1, Integer::sum);
        });

        return AnalysisResult.builder()
                .jobId(jobId)
                .status(Status.COMPLETED)
                .projectName(Optional.ofNullable(request.getProjectName()).orElse("Unnamed Project"))
                .startedAt(Optional.ofNullable(jobStore.get(jobId))
                        .map(AnalysisResult::getStartedAt).orElse(Instant.now()))
                .completedAt(Instant.now())

                // Summary counts
                .totalFindings(allFindings.size())
                .criticalCount(bySeverity.getOrDefault(Severity.CRITICAL, 0L).intValue())
                .highCount(bySeverity.getOrDefault(Severity.HIGH, 0L).intValue())
                .mediumCount(bySeverity.getOrDefault(Severity.MEDIUM, 0L).intValue())
                .lowCount(bySeverity.getOrDefault(Severity.LOW, 0L).intValue())
                .infoCount(bySeverity.getOrDefault(Severity.INFO, 0L).intValue())

                // Code stats
                .totalFilesScanned(sources.size())
                .totalLinesOfCode(totalLoc)
                .filesByLanguage(byLanguage)

                // Findings
                .securityFindings(securityFindings)
                .qualityFindings(qualityFindings)
                .architectureFindings(llmOutput.findings())

                // LLM narrative
                .executiveSummary(llmOutput.executiveSummary())
                .architecturalAssessment(llmOutput.architecturalAssessment())
                .topRecommendations(llmOutput.topRecommendations())

                .build();
    }
}
