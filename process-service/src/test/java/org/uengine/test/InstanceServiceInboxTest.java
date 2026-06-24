package org.uengine.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.messaging.polling.ExternalEventInboxServiceImpl;
import org.uengine.five.messaging.polling.dto.EventInboxBulkResponse;
import org.uengine.five.repository.EventMappingRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class InstanceServiceInboxTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void receiveInboxEvents_countsFailedRowsIndependently() {
        EventInboxRepository eventInboxRepository = Mockito.mock(EventInboxRepository.class);
        EventMappingRepository eventMappingRepository = Mockito.mock(EventMappingRepository.class);
        ExternalEventInboxServiceImpl service = new ExternalEventInboxServiceImpl(eventInboxRepository, eventMappingRepository);

        when(eventInboxRepository.save(any(EventInbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArrayNode events = objectMapper.createArrayNode();
        events.addNull();

        ObjectNode success = objectMapper.createObjectNode();
        success.put("corrkey", "corrkey1");
        success.put("eventname", "eventname1");
        success.set("payload", objectMapper.createObjectNode().put("value", 1));
        events.add(success);

        EventInboxBulkResponse response = service.receiveEvents(null, null, events);

        assertEquals("FAIL", response.getStatus());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailCount());
        assertEquals(1, response.getFailedList().size());
        assertEquals("0", response.getFailedList().get(0).getReasonCode());
        assertTrue(response.getFailedList().get(0).getReason().contains("event must not be null"));

        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertTrue(responseJson.contains("\"failedList\""));
        assertTrue(responseJson.contains("\"reasonCode\":\"0\""));
        assertTrue(responseJson.contains("\"reason\":\"event must not be null\""));

        verify(eventInboxRepository, times(1)).save(any(EventInbox.class));
    }
}