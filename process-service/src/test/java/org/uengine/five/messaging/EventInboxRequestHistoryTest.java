package org.uengine.five.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        verifyNoMoreInteractions(repository);
    }
}
