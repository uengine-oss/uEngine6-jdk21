package org.uengine.five.messaging;

public class NonRetryableInboxException extends RuntimeException {

    public NonRetryableInboxException(String message) {
        super(message);
    }
}
