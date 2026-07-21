package org.uengine.hwlife.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;
import org.uengine.five.messaging.EventInboxEnqueueService;
import org.uengine.five.messaging.EventInboxReceiveResult;
import org.uengine.hwlife.events.dto.ExternalEventInboxRequest;
import org.uengine.hwlife.events.dto.ExternalEventInboxResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 외부(External) Event Inbox 수신 서비스 구현.
 */
@Service
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class ExternalEventInboxServiceImpl implements ExternalEventInboxService {

    private final EventInboxEnqueueService enqueueService;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public ExternalEventInboxServiceImpl(EventInboxEnqueueService enqueueService) {
        this.enqueueService = enqueueService;
    }

    @Override
    public EventInboxReceiveResult receiveEvent(String requestBodyJson) {
        ExternalEventInboxRequest request;
        try {
            request = parseRequest(requestBodyJson);
        } catch (Exception e) {
            return EventInboxReceiveResult.failure(
                    ExternalEventInboxResponse.failed(null, null, "invalid payload: " + e.getMessage()));
        }

        String loanPcesMgmtNo = request.getLoanPcesMgmtNo();
        String evntNm = request.getEvntNm();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return EventInboxReceiveResult.failure(
                    ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, "invalid payload: " + e.getMessage()));
        }

        EventInboxResponse coreResponse = enqueueService.enqueue(
                new EventInboxRequest(evntNm, loanPcesMgmtNo, payloadJson));
        ExternalEventInboxResponse response = toExternalResponse(coreResponse, loanPcesMgmtNo, evntNm);
        if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
            return EventInboxReceiveResult.failure(response);
        }
        return EventInboxReceiveResult.success(response);
    }

    private ExternalEventInboxRequest parseRequest(String requestBodyJson) throws Exception {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return new ExternalEventInboxRequest();
        }
        return objectMapper.readValue(requestBodyJson, ExternalEventInboxRequest.class);
    }

    private ExternalEventInboxResponse toExternalResponse(
            EventInboxResponse coreResponse, String loanPcesMgmtNo, String evntNm) {
        if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
            return ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, coreResponse.getReason());
        }
        return ExternalEventInboxResponse.success(loanPcesMgmtNo, evntNm, "SUCCESS");
    }
}
