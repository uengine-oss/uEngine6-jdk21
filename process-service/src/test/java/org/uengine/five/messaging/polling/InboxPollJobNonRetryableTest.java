package org.uengine.five.messaging.polling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.uengine.five.messaging.NonRetryableInboxException;

class InboxPollJobNonRetryableTest {

    @Test
    void recognizesOwnershipFailureThroughWrappedCause() {
        RuntimeException wrapped = new RuntimeException(
                new NonRetryableInboxException("Work item must be claimed before completion"));

        assertTrue(InboxPollJob.isNonRetryable(wrapped));
        assertFalse(InboxPollJob.isNonRetryable(new RuntimeException("temporary failure")));
    }

    @org.junit.jupiter.api.Test
    void duplicateClassificationSurvivesWrappingWithoutMatchingMessageText() {
        org.junit.jupiter.api.Assertions.assertTrue(InboxPollJob.isDuplicate(
                new RuntimeException("wrapped", org.uengine.five.messaging.NonRetryableInboxException.duplicate())));
        org.junit.jupiter.api.Assertions.assertFalse(InboxPollJob.isDuplicate(
                new org.uengine.five.messaging.NonRetryableInboxException("중복")));
    }
}
