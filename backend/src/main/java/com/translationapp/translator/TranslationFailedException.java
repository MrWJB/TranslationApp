package com.translationapp.translator;

/**
 * Thrown when translation fails; message is safe to expose in logs (no secrets).
 */
public class TranslationFailedException extends RuntimeException {

    private final String providerId;
    private final boolean retryable;

    public TranslationFailedException(String providerId, String message, Throwable cause) {
        this(providerId, message, cause, false);
    }

    public TranslationFailedException(String providerId, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.providerId = providerId;
        this.retryable = retryable;
    }

    public String getProviderId() {
        return providerId;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
