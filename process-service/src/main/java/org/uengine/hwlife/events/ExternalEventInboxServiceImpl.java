package org.uengine.hwlife.events;

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
import org.uengine.hwlife.events.dto.ExternalItgtApvlInboxRequest;
import org.uengine.hwlife.events.dto.ExternalItgtApvlInboxResponse;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * 외부(External) Event Inbox 수신 서비스 구현.
 *
 * <p>ESB header/payload 전문을 받아 payload 업무 필드를 Inbox 에 enqueue 한다.
 * DB {@code payload} 컬럼에는 요청 payload 에 {@code esbHeader}
 * ({@code emnb}, {@code belnOrgnCode}) 를 추가한 JSON 을 저장한다.
 * 필수값 검증은 요청 payload 원문 문자열을 {@link ExternalEventInboxRequest} 로
 * 파싱해 수행한다(검증은 원문 기준, 저장 시에만 header 필드 병합).
 * 성공/실패 모두 동일 응답 형태(HTTP 200 + header/payload)이며,
 * 업무 결과는 항상 {@code payload}({@link ExternalEventInboxResponse}) 에 담는다.</p>
 */
@Service
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class ExternalEventInboxServiceImpl implements ExternalEventInboxService {

    private final EventInboxEnqueueService enqueueService;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();
    private final ExternalEsbInboxSupport esbSupport = new ExternalEsbInboxSupport(objectMapper);

    public ExternalEventInboxServiceImpl(EventInboxEnqueueService enqueueService) {
        this.enqueueService = enqueueService;
    }

    @Override
    public String getProviderId() {
        return "external";
    }

    /**
     * 외부 이벤트 Inbox 수신(enqueue).
     * <p>
     * ESB header 처리결과({@code prcsRsltDvsnCode}):
     * <ul>
     *   <li>{@code 0} — 성공 (업무 상세는 payload)</li>
     *   <li>{@code 1} — 실패 (시스템, 전문 파싱 불가 등)</li>
     * </ul>
     * payload 결과코드(prcsRsltCntn):
     * <ul>
     *   <li>LBM000000 - 정상 (Inbox enqueue 성공)</li>
     *   <li>LBM010001 - 요청 전문 파싱/역직렬화 실패 (시스템 실패)</li>
     *   <li>LBM010002 - 필수값 누락 (loanPcesMgmtNo, evntNm)</li>
     *   <li>LBM010003 - Inbox 멱등 중복 (동일 corrKey+eventName 이미 존재)</li>
     * </ul>
     */
    @Override
    public EventInboxReceiveResult receiveEvent(String requestBodyJson) {
        EsbCommonHeader header;
        ExternalEventInboxRequest payload;
        String rawPayloadJson;
        try {
            ExternalEsbInboxSupport.IncomingEsbRequest incoming =
                    esbSupport.parseIncomingRequest(requestBodyJson);
            header = incoming.header;
            rawPayloadJson = incoming.rawPayloadJson;
            payload = objectMapper.readValue(rawPayloadJson, ExternalEventInboxRequest.class);
        } catch (Exception e) {
            // 실패(시스템) — header prcsRsltDvsnCode=1
            return EventInboxReceiveResult.failed(
                    EsbEnvelope.failed(
                            null,
                            ExternalEventInboxResponse.failed(null, null, "LBM010001")));
        }

        String loanPcesMgmtNo = payload.getLoanPcesMgmtNo();
        String evntNm = payload.getEvntNm();

        if (ExternalEsbInboxSupport.isBlank(loanPcesMgmtNo)
                || ExternalEsbInboxSupport.isBlank(evntNm)) {
            // 성공 응답 + payload 업무 실패 (LBM010002)
            return EventInboxReceiveResult.success(
                    EsbEnvelope.success(
                            header,
                            ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, "LBM010002")));
        }

        String inboxPayload;
        try {
            inboxPayload = esbSupport.enrichPayloadWithHeaderFields(rawPayloadJson, header);
        } catch (Exception e) {
            return EventInboxReceiveResult.failed(
                    EsbEnvelope.failed(
                            header,
                            ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, "LBM010001")));
        }

        EventInboxResponse coreResponse = enqueueService.enqueue(
                new EventInboxRequest(evntNm, loanPcesMgmtNo, inboxPayload)
        );
        if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
            // 성공 응답 + payload 업무 실패 (멱등 중복 LBM010003)
            return EventInboxReceiveResult.success(
                    EsbEnvelope.success(
                            header,
                            ExternalEventInboxResponse.failed(loanPcesMgmtNo, evntNm, "LBM010003")));
        }
        // 성공
        return EventInboxReceiveResult.success(
                EsbEnvelope.success(header, ExternalEventInboxResponse.success(loanPcesMgmtNo, evntNm)));
    }

    /**
     * 통합승인(ItgtApvl) Inbox 수신({@code /inbox-apvl}).
     *
     * <p>{@code loanPcesMgmtNo} 기준:
     * <ul>
     *   <li>있으면 → 그 값이 corrKey (= loanPcesMgmtNo)</li>
     *   <li>없으면 → {@code 20 + yyyyMMdd + (BPM_EVENT_INBOX.id % 1000000)(6자리)} 채번
     *       (= corrKey = loanPcesMgmtNo), payload/응답에 반영</li>
     * </ul>
     * 필수값은 {@code evntNm} 만.</p>
     */
    @Override
    public EventInboxReceiveResult receiveApvlEvent(String requestBodyJson) {
        EsbCommonHeader header;
        ExternalItgtApvlInboxRequest payload;
        String rawPayloadJson;
        try {
            ExternalEsbInboxSupport.IncomingEsbRequest incoming =
                    esbSupport.parseIncomingRequest(requestBodyJson);
            header = incoming.header;
            rawPayloadJson = incoming.rawPayloadJson;
            payload = objectMapper.readValue(rawPayloadJson, ExternalItgtApvlInboxRequest.class);
        } catch (Exception e) {
            return EventInboxReceiveResult.failed(
                    EsbEnvelope.failed(
                            null,
                            ExternalItgtApvlInboxResponse.failed(null, null, "LBM010001")));
        }

        String loanPcesMgmtNo = payload.getLoanPcesMgmtNo();
        String evntNm = payload.getEvntNm();

        if (ExternalEsbInboxSupport.isBlank(evntNm)) {
            return EventInboxReceiveResult.success(
                    EsbEnvelope.success(
                            header,
                            ExternalItgtApvlInboxResponse.failed(loanPcesMgmtNo, evntNm, "LBM010002")));
        }

        String inboxPayload;
        try {
            inboxPayload = esbSupport.enrichPayloadWithHeaderFields(rawPayloadJson, header);
        } catch (Exception e) {
            return EventInboxReceiveResult.failed(
                    EsbEnvelope.failed(
                            header,
                            ExternalItgtApvlInboxResponse.failed(loanPcesMgmtNo, evntNm, "LBM010001")));
        }

        EventInboxResponse coreResponse;
        if (ExternalEsbInboxSupport.isBlank(loanPcesMgmtNo)) {
            try {
                coreResponse = enqueueService.enqueueWithCorrKeyFromId(
                        evntNm,
                        inboxPayload,
                        ExternalEsbInboxSupport::formatLoanPcesMgmtNo,
                        (payloadJson, corrKey) -> {
                            try {
                                return esbSupport.putLoanPcesMgmtNo(payloadJson, corrKey);
                            } catch (Exception e) {
                                throw new IllegalStateException(
                                        "failed to put loanPcesMgmtNo into inbox payload", e);
                            }
                        });
                // 채번 == corrKey == loanPcesMgmtNo
                loanPcesMgmtNo = coreResponse.getCorrKey();
            } catch (Exception e) {
                return EventInboxReceiveResult.failed(
                        EsbEnvelope.failed(
                                header,
                                ExternalItgtApvlInboxResponse.failed(null, evntNm, "LBM010001")));
            }
        } else {
            // 요청 loanPcesMgmtNo == corrKey
            coreResponse = enqueueService.enqueue(
                    new EventInboxRequest(evntNm, loanPcesMgmtNo, inboxPayload));
        }

        if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
            return EventInboxReceiveResult.success(
                    EsbEnvelope.success(
                            header,
                            ExternalItgtApvlInboxResponse.failed(loanPcesMgmtNo, evntNm, "LBM010003")));
        }
        return EventInboxReceiveResult.success(
                EsbEnvelope.success(header, ExternalItgtApvlInboxResponse.success(loanPcesMgmtNo, evntNm)));
    }
}
