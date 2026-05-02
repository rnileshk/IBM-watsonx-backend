package com.ibm.codeanalyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified job status response for polling endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatus {

    /**
     * Unique job identifier.
     */
    private String jobId;

    /**
     * Current status of the job.
     */
    private AnalysisResult.Status status;

    /**
     * Project name being analyzed.
     */
    private String projectName;

    /**
     * When the job started.
     */
    private Instant startedAt;

    /**
     * When the job completed (if finished).
     */
    private Instant completedAt;

    /**
     * Progress percentage (0-100).
     */
    private Integer progressPercent;

    /**
     * Current step being executed.
     */
    private String currentStep;

    /**
     * Error message if status is FAILED.
     */
    private String errorMessage;

    /**
     * Total findings count (available when completed).
     */
    private Integer totalFindings;

    /**
     * Create a status from an AnalysisResult.
     */
    public static JobStatus fromAnalysisResult(AnalysisResult result) {
        return JobStatus.builder()
                .jobId(result.getJobId())
                .status(result.getStatus())
                .projectName(result.getProjectName())
                .startedAt(result.getStartedAt())
                .completedAt(result.getCompletedAt())
                .errorMessage(result.getErrorMessage())
                .totalFindings(result.getTotalFindings())
                .build();
    }
}


