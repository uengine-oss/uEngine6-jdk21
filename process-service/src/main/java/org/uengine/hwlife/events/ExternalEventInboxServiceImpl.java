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
import org.uengine.hwlife.esbclient.support.EsbEnvelope;
import org.uengine.hwlife.events.dto.ExternalEventInboxRequest;
import org.uengine.hwlife.events.dto.ExternalEventInboxResponse;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * 외부(External) Event Inbox 수신 서비스 구현.
 *
 * <p>ESB {@code { header, payload }} 전문을 받아 payload 업무 필드를 Inbox 에 enqueue 한다.
 * 성공/실패 모두 동일 응답 형태(HTTP 200 + header/payload)이며,
 * 업무 결과는 항상 {@code payload}({@link ExternalEventInboxResponse}) 에 담는다.</p>
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
            return respond(null, ExternalEventInboxResponse.failed(null, null,
                    "invalid payload: " + e.getMessage()));
        }

        EsbCommonHeader header = esbRequest.getHeader();
        ExternalEventInboxRequest payload = esbRequest.getPayload();
        if (payload == null) {
            payload = new ExternalEventInboxRequest();
        }

        String loanPcesMgmtNo = payload.getLoanPcesMgmtNo();
        String evntNm = payload.getEvntNm();

        if (isBlank(loanPcesMgmtNo) || isBlank(evntNm)) {
            return respond(header, ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm,
                    "required field missing: loanPcesMgmtNo and evntNm are mandatory"));
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return respond(header, ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm,
                    "invalid payload: " + e.getMessage()));
        }

        EventInboxResponse coreResponse = enqueueService.enqueue(
                new EventInboxRequest(evntNm, loanPcesMgmtNo, payloadJson));
        return respond(header, toBusinessResponse(coreResponse, loanPcesMgmtNo, evntNm));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ExternalEventInboxResponse toBusinessResponse(
            EventInboxResponse coreResponse, String loanPcesMgmtNo, String evntNm) {
        if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
            return ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, coreResponse.getReason());
        }
        return ExternalEventInboxResponse.success(loanPcesMgmtNo, evntNm);
    }

    /**
     * 성공 응답과 동일한 봉투. 차이는 payload 메시지({@code prcsRsltCodeNm}/{@code prcsRsltCntn})뿐.
     * HTTP 는 항상 200 으로 내려가도록 {@link EventInboxReceiveResult#success} 만 사용한다.
     */
    private EventInboxReceiveResult respond(
            EsbCommonHeader header, ExternalEventInboxResponse businessPayload) {
        boolean ok = ExternalEventInboxResponse.STATUS_SUCCESS.equals(businessPayload.getPrcsRsltCodeNm());
        return EventInboxReceiveResult.success(
                ok
                        ? EsbEnvelope.success(header, businessPayload)
                        : EsbEnvelope.failed(header, businessPayload, businessPayload.getPrcsRsltCntn()));
    }
}
