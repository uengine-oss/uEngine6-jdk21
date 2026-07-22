package org.uengine.hwlife.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;
import org.uengine.five.messaging.EventInboxEnqueueService;
import org.uengine.five.messaging.EventInboxReceiveResult;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.dto.EsbRequest;
import org.uengine.hwlife.esbclient.dto.EsbResponse;
import org.uengine.hwlife.esbclient.support.EsbEnvelope;
import org.uengine.hwlife.events.dto.ExternalEventInboxRequest;
import org.uengine.hwlife.events.dto.ExternalEventInboxResponse;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * 외부(External) Event Inbox 수신 서비스 구현.
 *
 * <p>ESB {@code { header, payload }} 전문을 받아 payload 업무 필드를 Inbox 에 enqueue 한다.</p>
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
    public String getProviderId() {
        return "external";
    }

    @Override
    public EventInboxReceiveResult receiveEvent(String requestBodyJson) {
        EsbRequest<ExternalEventInboxRequest> esbRequest;
        try {
            esbRequest = EsbEnvelope.parseRequest(
                    objectMapper, requestBodyJson, ExternalEventInboxRequest.class);
        } catch (Exception e) {
            return EventInboxReceiveResult.failure(
                    toEsbResponse(null, ExternalEventInboxResponse.failed(null, null,
                            "invalid payload: " + e.getMessage()), false,
                            "invalid payload: " + e.getMessage()));
        }

        EsbCommonHeader header = esbRequest.getHeader();
        ExternalEventInboxRequest payload = esbRequest.getPayload();
        if (payload == null) {
            payload = new ExternalEventInboxRequest();
        }

        String loanPcesMgmtNo = payload.getLoanPcesMgmtNo();
        String evntNm = payload.getEvntNm();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            String reason = "invalid payload: " + e.getMessage();
            return EventInboxReceiveResult.failure(
                    toEsbResponse(header, ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, reason),
                            false, reason));
        }

        EventInboxResponse coreResponse = enqueueService.enqueue(
                new EventInboxRequest(evntNm, loanPcesMgmtNo, payloadJson));
        ExternalEventInboxResponse businessResponse = toBusinessResponse(coreResponse, loanPcesMgmtNo, evntNm);
        boolean failed = EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus());
        EsbResponse<ExternalEventInboxResponse> esbResponse = toEsbResponse(
                header, businessResponse, !failed, failed ? coreResponse.getReason() : null);
        return failed
                ? EventInboxReceiveResult.failure(esbResponse)
                : EventInboxReceiveResult.success(esbResponse);
    }

    private ExternalEventInboxResponse toBusinessResponse(
            EventInboxResponse coreResponse, String loanPcesMgmtNo, String evntNm) {
        if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
            return ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, coreResponse.getReason());
        }
        return ExternalEventInboxResponse.success(loanPcesMgmtNo, evntNm);
    }

    private EsbResponse<ExternalEventInboxResponse> toEsbResponse(
            EsbCommonHeader header,
            ExternalEventInboxResponse payload,
            boolean success,
            String reason) {
        return success
                ? EsbEnvelope.success(header, payload)
                : EsbEnvelope.failed(header, payload, reason);
    }
}
