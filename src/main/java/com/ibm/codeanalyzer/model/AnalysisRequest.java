package com.ibm.codeanalyzer.model;

import lombok.Data;

/**
 * Inbound analysis request — can be a Git URL, raw code, or reference to
 * an uploaded file (handled separately via multipart).
 */
@Data
public class AnalysisRequest {

    public enum InputType { GIT_URL, RAW_CODE, FILE_UPLOAD }

    /** What kind of input is being provided */
    private InputType inputType;

    /** Git repo URL (when inputType == GIT_URL) */
    private String gitUrl;

    /** Optional Git branch (defaults to main/master) */
    private String branch;

    /** Raw source code pasted directly (when inputType == RAW_CODE) */
    private String rawCode;

    /**
     * File name hint (used alongside a multipart upload so the service
     * knows which language / extension to expect).
     */
    private String fileName;

    /** Optional project name shown in the report */
    private String projectName;

    /** Whether to include LLM architectural analysis (may add latency) */
    private boolean includeLlmAnalysis = true;
}
