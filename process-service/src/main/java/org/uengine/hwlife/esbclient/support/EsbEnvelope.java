package org.uengine.hwlife.esbclient.support;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.uengine.hwlife.esbclient.dto.EsbCodes;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.dto.EsbRequest;
import org.uengine.hwlife.esbclient.dto.EsbResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ESB {@code { header, payload }} 봉투 파싱/응답 조립 헬퍼.
 *
 * <p>응답 header 규칙:
 * <ul>
 *   <li>시스템 공통부 + 요청정보 — 요청 header 를 에코</li>
 *   <li>응답정보 — 응답 시 채움 ({@code tlgrRspnDttm}, {@code prcsRsltDvsnCode})</li>
 * </ul>
 *
 * <p>처리결과({@code prcsRsltDvsnCode}):
 * <ul>
 *   <li>{@link #success} — {@code 0} : 정상 응답 (업무 성공/실패 상세는 payload)</li>
 *   <li>{@link #failed} — {@code 1} : 시스템 장애로 서비스 응답 자체 불가</li>
 * </ul>
 * 업무 결과 값은 항상 {@code payload} 에 둔다.</p>
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

    /** 성공 응답. {@code prcsRsltDvsnCode=0}. 업무 상세는 payload. */
    public static <R> EsbResponse<R> success(EsbCommonHeader requestHeader, R payload) {
        return respond(requestHeader, payload, EsbCodes.PRCS_RSLT_SUCCESS);
    }

    /** 실패 응답 (시스템). {@code prcsRsltDvsnCode=1}. */
    public static <R> EsbResponse<R> failed(EsbCommonHeader requestHeader, R payload) {
        return respond(requestHeader, payload, EsbCodes.PRCS_RSLT_FAILED);
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
            EsbCommonHeader requestHeader,
            R payload,
            String prcsRsltDvsnCode) {
        EsbCommonHeader header = copyHeader(requestHeader);
        clearResponseSection(header);
        header.setRspnDvsnCode("R");
        header.setTlgrRspnDttm(new SimpleDateFormat(EsbCodes.DTTM).format(new Date()));
        header.setPrcsRsltDvsnCode(prcsRsltDvsnCode);
        return new EsbResponse<>(header, payload);
    }

    /** 응답정보/메시지 구역만 비운다. (요청에 섞여 온 값 제거) */
    private static void clearResponseSection(EsbCommonHeader header) {
        header.setTlgrRspnDttm(null);
        header.setPrcsRsltDvsnCode(null);
        header.setTotalCount(null);
        header.setLastPageYn(null);
        header.setMsgeListCont(null);
        header.setMsgeList(null);
        header.setMsgeStackTrace(null);
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
