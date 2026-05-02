package com.ibm.codeanalyzer.model.exception;

/**
 * Exception thrown when a requested analysis job is not found.
 */
public class JobNotFoundException extends AnalysisException {

    private final String jobId;

    public JobNotFoundException(String jobId) {
        super("Analysis job not found: " + jobId);
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }
}


