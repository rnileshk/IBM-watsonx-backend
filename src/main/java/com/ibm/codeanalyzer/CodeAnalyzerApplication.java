package com.ibm.codeanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IBM Dev Bob Challenge — Monolith Code Analyzer
 *
 * Analyzes existing monolith codebases and provides:
 *   - Security vulnerability scanning (OWASP / SAST rules)
 *   - Code quality metrics (PMD, cyclomatic complexity)
 *   - AI-powered architectural improvement suggestions (LLM)
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CodeAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAnalyzerApplication.class, args);
    }
}
