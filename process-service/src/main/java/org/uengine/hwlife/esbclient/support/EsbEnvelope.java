package org.uengine.hwlife.esbclient.support;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.uengine.hwlife.esbclient.dto.EsbCodes;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.dto.EsbMessage;
import org.uengine.hwlife.esbclient.dto.EsbRequest;
import org.uengine.hwlife.esbclient.dto.EsbResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ESB {@code { header, payload }} 봉투 파싱/응답 조립 헬퍼.
 *
 * <p>outbound({@code EsbClient})·inbound(수신 API) 공통으로 사용한다.
 * 업무별 DTO 는 payload 타입으로만 두고, 봉투는 {@link EsbRequest}/{@link EsbResponse} 로 감싼다.</p>
 */
public final class EsbEnvelope {

    private static final ObjectMapper COPY_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EsbEnvelope() {
    }

    /**
     * 요청 JSON 을 {@link EsbRequest} 로 파싱한다.
     */
    public static <T> EsbRequest<T> parseRequest(
            ObjectMapper objectMapper, String bodyJson, Class<T> payloadType) throws Exception {
        Parsed<T> parsed = parse(objectMapper, bodyJson, payloadType);
        return new EsbRequest<>(parsed.header, parsed.payload);
    }

    /**
     * 응답 JSON 을 {@link EsbResponse} 로 파싱한다. (outbound 호출 응답 등)
     */
    public static <T> EsbResponse<T> parseResponse(
            ObjectMapper objectMapper, String bodyJson, Class<T> payloadType) throws Exception {
        Parsed<T> parsed = parse(objectMapper, bodyJson, payloadType);
        return new EsbResponse<>(parsed.header, parsed.payload);
    }

    /** 성공 응답 — 요청 header 를 복사·에코하고 {@code prcsRsltDvsnCode=0} 을 세팅한다. */
    public static <R> EsbResponse<R> success(EsbCommonHeader requestHeader, R payload) {
        return respond(requestHeader, payload, true, null);
    }

    /** 실패 응답 — 요청 header 를 복사·에코하고 {@code prcsRsltDvsnCode=1}, msgeList 에 사유를 담는다. */
    public static <R> EsbResponse<R> failed(EsbCommonHeader requestHeader, R payload, String reason) {
        return respond(requestHeader, payload, false, reason);
    }

    private static <T> Parsed<T> parse(
            ObjectMapper objectMapper, String bodyJson, Class<T> payloadType) throws Exception {
        if (bodyJson == null || bodyJson.isBlank()) {
            return new Parsed<>(null, null);
        }
        JsonNode root = objectMapper.readTree(bodyJson);
        EsbCommonHeader header = null;
        T payload = null;
        if (root.hasNonNull("header")) {
            header = objectMapper.treeToValue(root.get("header"), EsbCommonHeader.class);
        }
        if (root.has("payload") && !root.get("payload").isNull() && payloadType != null
                && payloadType != Void.class) {
            payload = objectMapper.convertValue(root.get("payload"), payloadType);
        }
        return new Parsed<>(header, payload);
    }

    private static <R> EsbResponse<R> respond(
            EsbCommonHeader requestHeader, R payload, boolean success, String reason) {
        EsbCommonHeader header = copyHeader(requestHeader);
        header.setPrcsRsltDvsnCode(success ? EsbCodes.PRCS_RSLT_SUCCESS : EsbCodes.PRCS_RSLT_FAILED);
        header.setTlgrRspnDttm(LocalDateTime.now().format(EsbCodes.DTTM));
        if (!success && reason != null && !reason.isBlank()) {
            EsbMessage message = new EsbMessage();
            message.setMsgeCntn(reason);
            List<EsbMessage> list = Collections.singletonList(message);
            header.setMsgeList(list);
            header.setMsgeListCont(list.size());
        }
        return new EsbResponse<>(header, payload);
    }

    /** 요청 header 를 응답용으로 복사한다. (원본 mutate 방지) */
    static EsbCommonHeader copyHeader(EsbCommonHeader source) {
        if (source == null) {
            return new EsbCommonHeader();
        }
        return COPY_MAPPER.convertValue(source, EsbCommonHeader.class);
    }

    private static final class Parsed<T> {
        final EsbCommonHeader header;
        final T payload;

        Parsed(EsbCommonHeader header, T payload) {
            this.header = header;
            this.payload = payload;
        }
    }
}
