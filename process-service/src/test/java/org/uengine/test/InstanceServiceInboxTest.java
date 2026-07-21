package org.uengine.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.service.InstanceServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;

public class InstanceServiceInboxTest {

    // private final ObjectMapper objectMapper = new ObjectMapper();

    // @Test
    // public void receiveInboxEvents_countsFailedRowsWhenInsertFails() throws Exception {
    //     InstanceServiceImpl service = new InstanceServiceImpl();
    //     EventInboxRepository eventInboxRepository = Mockito.mock(EventInboxRepository.class);
    //     ReflectionTestUtils.setField(service, "eventInboxRepository", eventInboxRepository);

    //     when(eventInboxRepository.save(any(EventInbox.class)))
    //             .thenAnswer(invocation -> invocation.getArgument(0))
    //             .thenThrow(new DataIntegrityViolationException("duplicate inbox event"));

    //     InstanceServiceImpl.EventInboxRequest success = new InstanceServiceImpl.EventInboxRequest();
    //     success.setCorrKey("corrkey1");
    //     success.setEventName("eventname1");
    //     success.setPayload(objectMapper.readTree("{\"value\":1}"));

    //     InstanceServiceImpl.EventInboxRequest failure = new InstanceServiceImpl.EventInboxRequest();
    //     failure.setCorrKey("corrkey2");
    //     failure.setEventName("eventname2");
    //     failure.setPayload(objectMapper.readTree("{\"value\":2}"));

    //     ResponseEntity<InstanceServiceImpl.EventInboxBulkResponse> responseEntity =
    //             service.receiveInboxEvents(null, null, List.of(success, failure));
    //     InstanceServiceImpl.EventInboxBulkResponse response = responseEntity.getBody();

    //     assertEquals(202, responseEntity.getStatusCode().value());
    //     assertEquals("FAIL", response.getStatus());
    //     assertEquals(1, response.getSuccessCount());
    //     assertEquals(1, response.getFailCount());
    //     assertEquals(1, response.getFailedList().size());
    //     assertEquals("corrkey2", response.getFailedList().get(0).getCorrKey());
    //     assertEquals("eventname2", response.getFailedList().get(0).getEventName());
    //     assertEquals("0", response.getFailedList().get(0).getReasonCode());
    //     assertTrue(response.getFailedList().get(0).getReason().contains("duplicate inbox event"));

    //     String responseJson = objectMapper.writeValueAsString(response);
    //     assertTrue(responseJson.contains("\"failedList\""));
    //     assertTrue(responseJson.contains("\"corrkey\":\"corrkey2\""));
    //     assertTrue(responseJson.contains("\"eventname\":\"eventname2\""));
    //     assertTrue(responseJson.contains("\"reasonCode\":\"0\""));
    //     assertTrue(responseJson.contains("\"reason\":\"data integrity violation: duplicate inbox event\""));

    //     verify(eventInboxRepository, times(2)).save(any(EventInbox.class));
    // }
}
