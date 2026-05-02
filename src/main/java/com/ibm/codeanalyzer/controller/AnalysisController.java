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
 * POST /api/v1/analyze/url        — analyze a public Git repository
 * POST /api/v1/analyze/code       — analyze raw pasted code
 * POST /api/v1/analyze/upload     — analyze an uploaded .zip or .java file
 * GET  /api/v1/analyze/{jobId}    — poll analysis status / retrieve results
 * GET  /api/v1/analyze/jobs       — list all job IDs
 * GET  /api/v1/health             — liveness probe
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

    private final IngestionService     ingestionService;
    private final AnalysisOrchestrator orchestrator;

    // ── POST /api/v1/analyze/url ──────────────────────────────────────
    @PostMapping("/analyze/url")
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
                        "poll", "/api/v1/analyze/" + jobId));
    }

    // ── POST /api/v1/analyze/code ─────────────────────────────────────
    @PostMapping("/analyze/code")
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
                        "poll", "/api/v1/analyze/" + jobId));
    }

    // ── POST /api/v1/analyze/upload ───────────────────────────────────
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
                        "poll", "/api/v1/analyze/" + jobId));
    }

    // ── GET /api/v1/analyze/{jobId} ───────────────────────────────────
    @GetMapping("/analyze/{jobId}")
    public ResponseEntity<AnalysisResult> getResult(@PathVariable String jobId) {
        return orchestrator.getResult(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/v1/analyze/jobs ──────────────────────────────────────
    @GetMapping("/analyze/jobs")
    public ResponseEntity<List<String>> listJobs() {
        return ResponseEntity.ok(orchestrator.listJobs());
    }

    // ── GET /api/v1/health ────────────────────────────────────────────
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
