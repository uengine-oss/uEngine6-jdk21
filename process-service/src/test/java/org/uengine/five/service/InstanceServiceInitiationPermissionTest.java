package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.uengine.kernel.UEngineException;

class InstanceServiceInitiationPermissionTest {

    @Test
    void recognizesWrappedInitiationPermissionFailure() {
        Exception wrapped = new Exception(new UEngineException(
                "You (hong) are not permitted to initiate this process."));

        assertTrue(InstanceServiceImpl.isInitiationPermissionFailure(wrapped));
    }

    @Test
    void leavesOtherExecutionFailuresUnchanged() {
        assertFalse(InstanceServiceImpl.isInitiationPermissionFailure(
                new IllegalStateException("definition could not be loaded")));
    }
}
