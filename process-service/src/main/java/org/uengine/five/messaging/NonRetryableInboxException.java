package org.uengine.five.messaging;

public class NonRetryableInboxException extends RuntimeException {

    private final boolean duplicate;

    public NonRetryableInboxException(String message) {
        this(message, false);
    }

    private NonRetryableInboxException(String message, boolean duplicate) {
        super(message);
        this.duplicate = duplicate;
    }

    public static NonRetryableInboxException duplicate() {
        return new NonRetryableInboxException("중복", true);
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
