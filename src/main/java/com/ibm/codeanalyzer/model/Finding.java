package com.ibm.codeanalyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * A single finding from any analysis pass (security, quality, or LLM).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Finding {

    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
    public enum Category { SECURITY, CODE_QUALITY, ARCHITECTURE, PERFORMANCE, DEPENDENCY }

    private Severity severity;
    private Category category;

    /** Short human-readable title */
    private String title;

    /** Full description of the problem */
    private String description;

    /** Suggested fix or improvement */
    private String recommendation;

    /** File path relative to project root */
    private String filePath;

    /** Line number where the issue was found (null if not applicable) */
    private Integer lineNumber;

    /** Rule ID that triggered this finding (e.g. PMD rule name, OWASP rule) */
    private String ruleId;

    /** Source tool: PMD | SECURITY_SCANNER | LLM | AST */
    private String source;
}
