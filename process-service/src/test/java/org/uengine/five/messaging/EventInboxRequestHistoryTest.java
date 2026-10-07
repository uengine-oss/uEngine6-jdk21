package org.uengine.five.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.Optional;
import org.uengine.five.dto.EventInboxRequest;

class EventInboxRequestHistoryTest {
    @Test void savesSeparateRowsAndPayloadsForTheSameKey() {
        EventInboxRepository repository = mock(EventInboxRepository.class);
        EventInboxEnqueueServiceImpl service = new EventInboxEnqueueServiceImpl(repository);
        service.enqueue(new EventInboxRequest("DONE", "KEY", "{\"value\":1}"));
        service.enqueue(new EventInboxRequest("DONE", "KEY", "{\"value\":2}"));
        ArgumentCaptor<EventInbox> rows = ArgumentCaptor.forClass(EventInbox.class);
        verify(repository, times(2)).save(rows.capture());
        assertNotSame(rows.getAllValues().get(0), rows.getAllValues().get(1));
        assertEquals("{\"value\":1}", rows.getAllValues().get(0).getPayload());
        assertEquals("{\"value\":2}", rows.getAllValues().get(1).getPayload());
        verify(repository, times(2)).findFirstByCorrKeyAndEventNameOrderByIdDesc("KEY", "DONE");
        verifyNoMoreInteractions(repository);
    }

    @Test void reusesLatestFailureExceptDuplicate() {
        EventInboxRepository repository = mock(EventInboxRepository.class);
        EventInbox existing = new EventInbox();
        existing.setStatus("FAILED");
        existing.setLastError("Actor is not assigned");
        existing.setProcessedAt(Instant.now());
        existing.setTryCnt(3);
        when(repository.findFirstByCorrKeyAndEventNameOrderByIdDesc("KEY", "DONE"))
                .thenReturn(Optional.of(existing));
        new EventInboxEnqueueServiceImpl(repository).enqueue(new EventInboxRequest("DONE", "KEY", "{}"));
        verify(repository).save(existing);
        assertEquals("PENDING", existing.getStatus());
        assertEquals("{}", existing.getPayload());
        assertEquals(0, existing.getTryCnt());
        assertNull(existing.getLastError());
        assertNull(existing.getProcessedAt());
    }

    @Test void preservesDuplicateFailureAndCreatesNewRequest() {
        assertCreatesNewRow("FAILED", "중복");
    }

    @Test void preservesSuccessfulAndPendingRequests() {
        assertCreatesNewRow("SUCCESS", null);
        assertCreatesNewRow("PENDING", null);
    }

    private void assertCreatesNewRow(String status, String error) {
        EventInboxRepository repository = mock(EventInboxRepository.class);
        EventInbox existing = new EventInbox();
        existing.setStatus(status);
        existing.setLastError(error);
        when(repository.findFirstByCorrKeyAndEventNameOrderByIdDesc("KEY", "DONE"))
                .thenReturn(Optional.of(existing));
        new EventInboxEnqueueServiceImpl(repository).enqueue(new EventInboxRequest("DONE", "KEY", "{}"));
        ArgumentCaptor<EventInbox> row = ArgumentCaptor.forClass(EventInbox.class);
        verify(repository).save(row.capture());
        assertNotSame(existing, row.getValue());
        assertEquals(status, existing.getStatus());
        assertEquals(error, existing.getLastError());
    }
}
