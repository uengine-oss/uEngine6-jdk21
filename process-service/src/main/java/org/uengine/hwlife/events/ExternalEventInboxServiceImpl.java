package org.uengine.hwlife.events;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;
import org.uengine.five.messaging.EventInboxEnqueueService;
import org.uengine.five.messaging.EventInboxReceiveResult;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbEnvelope;
import org.uengine.hwlife.events.dto.ExternalEventInboxRequest;
import org.uengine.hwlife.events.dto.ExternalEventInboxResponse;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * 외부(External) Event Inbox 수신 서비스 구현.
 *
 * <p>ESB {@code { header, payload }} 전문을 받아 payload 업무 필드를 Inbox 에 enqueue 한다.
 * DB {@code payload} 컬럼에는 요청 전문의 {@code payload} 값을 원문 그대로(String) 저장한다.
 * 필수값 검증은 저장할 {@code payload} 원문 문자열을 {@link ExternalEventInboxRequest} 로
 * 한 번 더 파싱해 수행한다(검증·저장 기준 일치).
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
        EsbCommonHeader header;
        ExternalEventInboxRequest payload;
        String payloadJson;
        try {
            IncomingEsbRequest incoming = parseIncomingRequest(requestBodyJson);
            header = incoming.header;
            payloadJson = incoming.payloadJson;
            payload = objectMapper.readValue(payloadJson, ExternalEventInboxRequest.class);
        } catch (Exception e) {
            return respond(null, ExternalEventInboxResponse.failed(null, null,
                    "invalid payload: " + e.getMessage()));
        }

        String loanPcesMgmtNo = payload.getLoanPcesMgmtNo();
        String evntNm = payload.getEvntNm();

        if (isBlank(loanPcesMgmtNo) || isBlank(evntNm)) {
            return respond(header, ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm,
                    "required field missing: loanPcesMgmtNo and evntNm are mandatory"));
        }

        EventInboxResponse coreResponse = enqueueService.enqueue(
                new EventInboxRequest(evntNm, loanPcesMgmtNo, payloadJson));
        return respond(header, toBusinessResponse(coreResponse, loanPcesMgmtNo, evntNm));
    }

    /**
     * 요청 본문을 한 번 스트리밍 파싱해 {@code header} 와 {@code payload} 원문을 추출한다.
     */
    private IncomingEsbRequest parseIncomingRequest(String requestBodyJson) throws IOException {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return new IncomingEsbRequest(null, "{}");
        }
        EsbCommonHeader header = null;
        String payloadJson = null;
        try (JsonParser parser = objectMapper.createParser(requestBodyJson)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token != JsonToken.FIELD_NAME) {
                    continue;
                }
                String fieldName = parser.currentName();
                token = parser.nextToken();
                if (token == null) {
                    break;
                }
                if ("header".equals(fieldName)) {
                    if (header == null) {
                        header = parseHeaderValue(token, parser);
                    } else {
                        skipJsonValue(parser, token);
                    }
                    continue;
                }
                if ("payload".equals(fieldName)) {
                    if (payloadJson == null) {
                        payloadJson = sliceJsonValue(requestBodyJson, parser, token);
                    } else {
                        skipJsonValue(parser, token);
                    }
                    continue;
                }
                skipJsonValue(parser, token);
            }
        }
        return new IncomingEsbRequest(header, payloadJson != null ? payloadJson : "{}");
    }

    private EsbCommonHeader parseHeaderValue(JsonToken token, JsonParser parser) throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        JsonNode headerNode = objectMapper.readTree(parser);
        return objectMapper.treeToValue(headerNode, EsbCommonHeader.class);
    }

    /**
     * 요청 JSON 에서 현재 토큰 위치의 값을 원문 substring 으로 추출한다.
     */
    private String sliceJsonValue(String requestBodyJson, JsonParser parser, JsonToken token)
            throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            return "{}";
        }
        int start = (int) parser.currentTokenLocation().getCharOffset();
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
            int end = (int) parser.currentTokenLocation().getCharOffset() + 1;
            return requestBodyJson.substring(start, end);
        }
        parser.nextToken();
        int end = (int) parser.currentTokenLocation().getCharOffset();
        return requestBodyJson.substring(start, end);
    }

    private void skipJsonValue(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return;
        }
        if (token != JsonToken.VALUE_NULL) {
            parser.nextToken();
        }
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

    private static final class IncomingEsbRequest {
        final EsbCommonHeader header;
        final String payloadJson;

        IncomingEsbRequest(EsbCommonHeader header, String payloadJson) {
            this.header = header;
            this.payloadJson = payloadJson;
        }
    }
}
