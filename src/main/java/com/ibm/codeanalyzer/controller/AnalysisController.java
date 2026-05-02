package com.ibm.codeanalyzer.controller;

import com.ibm.codeanalyzer.model.AnalysisRequest;
import com.ibm.codeanalyzer.model.AnalysisRequest.InputType;
import com.ibm.codeanalyzer.model.AnalysisResult;
import com.ibm.codeanalyzer.service.AnalysisOrchestrator;
import com.ibm.codeanalyzer.service.IngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API — Code Analyzer
 *
 * POST /api/analyze/git           — analyze a public Git repository
 * POST /api/analyze/raw           — analyze raw pasted code
 * POST /api/analyze/upload        — analyze an uploaded .zip or .java file
 * GET  /api/analyze/result/{jobId} — retrieve analysis results
 * GET  /api/analyze/status/{jobId} — poll analysis status
 * POST /api/analyze/cancel/{jobId} — cancel a running job
 * GET  /api/health                — liveness probe
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final IngestionService     ingestionService;
    private final AnalysisOrchestrator orchestrator;

    // ── POST /api/analyze/git ──────────────────────────────────────
    @PostMapping("/analyze/git")
    public ResponseEntity<Map<String, String>> analyzeGitRepo(
            @RequestBody @Valid GitUrlRequest req) {

        String jobId = UUID.randomUUID().toString();
        log.info("New git-url job [{}]: {}", jobId, req.gitUrl());

        AnalysisRequest request = new AnalysisRequest();
        request.setInputType(InputType.GIT_URL);
        request.setGitUrl(req.gitUrl());
        request.setBranch(req.branch());
        request.setProjectName(req.projectName());
        request.setIncludeLlmAnalysis(req.includeLlmAnalysis());

        Map<String, String> sources = ingestionService.ingestSources(request, jobId);
        orchestrator.runAsync(request, sources, jobId);

        return ResponseEntity.accepted()
                .body(Map.of("jobId", jobId, "status", "RUNNING",
                        "poll", "/api/analyze/status/" + jobId));
    }

    // ── POST /api/analyze/raw ─────────────────────────────────────────
    @PostMapping("/analyze/raw")
    public ResponseEntity<Map<String, String>> analyzeRawCode(
            @RequestBody @Valid RawCodeRequest req) {

        String jobId = UUID.randomUUID().toString();
        log.info("New raw-code job [{}]", jobId);

        AnalysisRequest request = new AnalysisRequest();
        request.setInputType(InputType.RAW_CODE);
        request.setRawCode(req.code());
        request.setFileName(req.fileName());
        request.setProjectName(req.projectName());
        request.setIncludeLlmAnalysis(req.includeLlmAnalysis());

        Map<String, String> sources = ingestionService.ingestSources(request, jobId);
        orchestrator.runAsync(request, sources, jobId);

        return ResponseEntity.accepted()
                .body(Map.of("jobId", jobId, "status", "RUNNING",
                        "poll", "/api/analyze/status/" + jobId));
    }

    // ── POST /api/analyze/upload ───────────────────────────────────
    @PostMapping(value = "/analyze/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> analyzeUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "projectName",       required = false) String projectName,
            @RequestParam(value = "includeLlmAnalysis", defaultValue = "true") boolean includeLlm) {

        String jobId = UUID.randomUUID().toString();
        log.info("New upload job [{}]: {}", jobId, file.getOriginalFilename());

        AnalysisRequest request = new AnalysisRequest();
        request.setInputType(InputType.FILE_UPLOAD);
        request.setProjectName(projectName);
        request.setIncludeLlmAnalysis(includeLlm);

        Map<String, String> sources = ingestionService.ingestFromUpload(file, jobId);
        orchestrator.runAsync(request, sources, jobId);

        return ResponseEntity.accepted()
                .body(Map.of("jobId", jobId, "status", "RUNNING",
                        "poll", "/api/analyze/status/" + jobId));
    }

    // ── GET /api/analyze/result/{jobId} ───────────────────────────────
    @GetMapping("/analyze/result/{jobId}")
    public ResponseEntity<AnalysisResult> getResult(@PathVariable String jobId) {
        return orchestrator.getResult(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/analyze/status/{jobId} ───────────────────────────────
    @GetMapping("/analyze/status/{jobId}")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String jobId) {
        return orchestrator.getResult(jobId)
                .map(result -> ResponseEntity.ok(Map.of(
                        "jobId", jobId,
                        "status", result.getStatus().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/analyze/cancel/{jobId} ──────────────────────────────
    @PostMapping("/analyze/cancel/{jobId}")
    public ResponseEntity<Map<String, String>> cancelJob(@PathVariable String jobId) {
        // TODO: Implement job cancellation logic
        return ResponseEntity.ok(Map.of("jobId", jobId, "status", "CANCELLED"));
    }

    // ── GET /api/health ────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "IBM Code Analyzer"));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Request records
    // ─────────────────────────────────────────────────────────────────

    public record GitUrlRequest(
            @NotBlank String gitUrl,
            String branch,
            String projectName,
            boolean includeLlmAnalysis
    ) {}

    public record RawCodeRequest(
            @NotBlank String code,
            String fileName,
            String projectName,
            boolean includeLlmAnalysis
    ) {}

}
