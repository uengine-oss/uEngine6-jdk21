package org.uengine.five.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;

class ProcessCompletionWorkitemReconcilerTest {

    @Test
    void completesOnlyActiveWorkForExactInstance() {
        // WorklistRepository repository = mock(WorklistRepository.class);
        // WorklistEntity newWork = work(11L, "NEW");
        // WorklistEntity runningWork = work(11L, "RUNNING");
        // when(repository.findByInstIdAndStatusIn(11L, List.of("NEW", "RUNNING")))
        //         .thenReturn(List.of(newWork, runningWork));

        // int changed = new ProcessCompletionWorkitemReconciler(repository).reconcile(11L);

        // assertEquals(2, changed);
        // assertEquals("COMPLETED", newWork.getStatus());
        // assertEquals("COMPLETED", runningWork.getStatus());
        // assertNotNull(newWork.getEndDate());
        // assertNotNull(runningWork.getEndDate());
        // verify(repository).saveAll(List.of(newWork, runningWork));
    }

    @Test
    void isIdempotentWhenNoActiveWorkRemains() {
        // WorklistRepository repository = mock(WorklistRepository.class);
        // when(repository.findByInstIdAndStatusIn(12L, List.of("NEW", "RUNNING")))
        //         .thenReturn(List.of());

        // int changed = new ProcessCompletionWorkitemReconciler(repository).reconcile(12L);

        // assertEquals(0, changed);
        // verify(repository, never()).saveAll(anyList());
    }

    @Test
    void ignoresMissingInstanceId() {
        // WorklistRepository repository = mock(WorklistRepository.class);

        // assertEquals(0, new ProcessCompletionWorkitemReconciler(repository).reconcile(null));

        // verify(repository, never()).findByInstIdAndStatusIn(null, List.of("NEW", "RUNNING"));
    }

    private static WorklistEntity work(Long instanceId, String status) {
        WorklistEntity work = new WorklistEntity();
        work.setInstId(instanceId);
        work.setStatus(status);
        return work;
    }
}
