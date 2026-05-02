package com.ibm.codeanalyzer.model.exception;

/**
 * Exception thrown when user input is invalid.
 */
public class InvalidInputException extends AnalysisException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}


