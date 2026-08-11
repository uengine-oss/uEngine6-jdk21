package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.messaging.NonRetryableInboxException;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.ProcessInstance;

class AsyncEventListenerCompletionClaimTest {

    private AsyncEventListener listener;
    private WorklistRepository worklistRepository;
    private ProcessInstance instance;
    private HumanActivity activity;

    @BeforeEach
    void setUp() throws Exception {
        listener = new AsyncEventListener();
        worklistRepository = mock(WorklistRepository.class);
        listener.worklistRepository = worklistRepository;
        instance = mock(ProcessInstance.class);
        activity = mock(HumanActivity.class);
        when(activity.getTracingTag()).thenReturn("task1");
        when(activity.getTaskIds(instance)).thenReturn(new String[] { "42" });
    }

    @Test
    void acceptsAnyClaimedWorkItemWithoutRequesterComparison() throws Exception {
        WorklistEntity worklist = new WorklistEntity();
        worklist.setTaskId(42L);
        worklist.setEndpoint("hong");
        when(worklistRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(worklist));

        listener.validateHumanActivityCompletion(instance, activity);

        verify(instance).setProperty("task1", HumanActivity.PVKEY_TASKID, "42");
    }

    @Test
    void rejectsUnclaimedWorkItem() {
        WorklistEntity worklist = new WorklistEntity();
        worklist.setTaskId(42L);
        when(worklistRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(worklist));

        assertThrows(NonRetryableInboxException.class,
                () -> listener.validateHumanActivityCompletion(instance, activity));
    }
}
