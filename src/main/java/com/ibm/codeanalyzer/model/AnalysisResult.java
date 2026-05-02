package com.ibm.codeanalyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Full analysis report returned to the API caller.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResult {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    // ── Job identity ─────────────────────────────────────────
    private String jobId;
    private Status status;
    private String projectName;
    private Instant startedAt;
    private Instant completedAt;

    // ── Summary counts ───────────────────────────────────────
    private int totalFindings;
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;

    // ── Code stats ───────────────────────────────────────────
    private int totalFilesScanned;
    private int totalLinesOfCode;
    private Map<String, Integer> filesByLanguage;

    // ── Findings by category ─────────────────────────────────
    private List<Finding> securityFindings;
    private List<Finding> qualityFindings;
    private List<Finding> architectureFindings;

    // ── LLM narrative summary ────────────────────────────────
    private String executiveSummary;
    private String architecturalAssessment;
    private List<String> topRecommendations;

    // ── Error detail (if status == FAILED) ───────────────────
    private String errorMessage;
}
