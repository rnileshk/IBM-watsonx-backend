package com.ibm.codeanalyzer.model.exception;

/**
 * Exception thrown when LLM API calls fail.
 */
public class LLMException extends AnalysisException {

    private final String provider;
    private final Integer statusCode;

    public LLMException(String message, String provider) {
        super(message);
        this.provider = provider;
        this.statusCode = null;
    }

    public LLMException(String message, String provider, Integer statusCode) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public LLMException(String message, String provider, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = null;
    }

    public String getProvider() {
        return provider;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    @Override
    public String getMessage() {
        StringBuilder msg = new StringBuilder(super.getMessage());
        if (provider != null) {
            msg.append(" [Provider: ").append(provider).append("]");
        }
        if (statusCode != null) {
            msg.append(" [Status: ").append(statusCode).append("]");
        }
        return msg.toString();
    }
}


