package com.ibm.codeanalyzer.model.exception;

/**
 * Exception thrown when source code ingestion fails.
 */
public class IngestionException extends AnalysisException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}


