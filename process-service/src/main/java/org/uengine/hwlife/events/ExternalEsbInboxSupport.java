package org.uengine.hwlife.events;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * External Inbox 공통 — ESB header/payload 원문 파싱 및 Inbox 저장용 payload enrich.
 */
final class ExternalEsbInboxSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectMapper objectMapper;

    ExternalEsbInboxSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 요청 본문을 한 번 스트리밍 파싱해 {@code header} 와 {@code payload} 원문을 추출한다.
     */
    IncomingEsbRequest parseIncomingRequest(String requestBodyJson) throws IOException {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return new IncomingEsbRequest(null, "{}");
        }
        EsbCommonHeader header = null;
        String rawPayloadJson = null;
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
                    if (rawPayloadJson == null) {
                        rawPayloadJson = sliceJsonValue(requestBodyJson, parser, token);
                    } else {
                        skipJsonValue(parser, token);
                    }
                    continue;
                }
                skipJsonValue(parser, token);
            }
        }
        return new IncomingEsbRequest(header, rawPayloadJson != null ? rawPayloadJson : requestBodyJson);
    }

    private EsbCommonHeader parseHeaderValue(JsonToken token, JsonParser parser) throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        JsonNode headerNode = objectMapper.readTree(parser);
        return objectMapper.treeToValue(headerNode, EsbCommonHeader.class);
    }

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

    /**
     * Inbox 저장용 payload enrich.
     * <ul>
     *   <li>ESB header 의 {@code emnb}/{@code belnOrgnCode} → {@code esbHeader} 객체 (있을 때만)</li>
     *   <li>{@code bpmBswrClsfCode} — 호출부가 결정한 값
     *       ({@code /inbox}=요청값 또는 기본 10, {@code /inbox-apvl}=요청값 또는 기본 20)</li>
     * </ul>
     */
    String enrichInboxPayload(String payloadJson, EsbCommonHeader header, String bpmBswrClsfCode)
            throws IOException {
        JsonNode root = objectMapper.readTree(payloadJson != null ? payloadJson : "{}");
        ObjectNode object = root.isObject()
                ? (ObjectNode) root
                : objectMapper.createObjectNode();

        if (header != null) {
            String emnb = header.getEmnb();
            String belnOrgnCode = header.getBelnOrgnCode();
            if (!isBlank(emnb) || !isBlank(belnOrgnCode)) {
                ObjectNode esbHeader = objectMapper.createObjectNode();
                if (!isBlank(emnb)) {
                    esbHeader.put("emnb", emnb.trim());
                }
                if (!isBlank(belnOrgnCode)) {
                    esbHeader.put("belnOrgnCode", belnOrgnCode.trim());
                }
                object.set("esbHeader", esbHeader);
            }
        }
        object.put("bpmBswrClsfCode", bpmBswrClsfCode);
        return objectMapper.writeValueAsString(object);
    }

    /**
     * 대출프로세스관리번호(=corrKey) 채번:
     * {@code 20} + {@code yyyyMMdd}(Asia/Seoul) + {@code BPM_EVENT_INBOX.id % 1_000_000} 6자리.
     *
     * <p>PK 시퀀스({@code SEQ_BPM_EVENT_INBOX}, allocationSize=50, NO CYCLE)는 그대로 두고,
     * 업무번호 6자리만 나머지({@code id % 1000000})로 맞춘다.
     * 예) id=1 → {@code ...000001}, id=1000001 → {@code ...000001}</p>
     */
    static String formatLoanPcesMgmtNo(Long inboxId) {
        if (inboxId == null) {
            throw new IllegalArgumentException("inboxId is required");
        }
        String date = LocalDate.now(SEOUL).format(YMD);
        long seq6 = Math.floorMod(inboxId, 1_000_000L);
        return "20" + date + String.format("%06d", seq6);
    }

    /** payload JSON 에 {@code loanPcesMgmtNo}(=corrKey) 를 넣거나 덮어쓴다. */
    String putLoanPcesMgmtNo(String payloadJson, String loanPcesMgmtNo) throws IOException {
        JsonNode root = objectMapper.readTree(payloadJson != null ? payloadJson : "{}");
        if (!root.isObject()) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("loanPcesMgmtNo", loanPcesMgmtNo);
            return objectMapper.writeValueAsString(wrapper);
        }
        ObjectNode object = (ObjectNode) root;
        object.put("loanPcesMgmtNo", loanPcesMgmtNo);
        return objectMapper.writeValueAsString(object);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** parseIncomingRequest 반환용 — header + payload 원문. */
    static final class IncomingEsbRequest {
        final EsbCommonHeader header;
        final String rawPayloadJson;

        IncomingEsbRequest(EsbCommonHeader header, String rawPayloadJson) {
            this.header = header;
            this.rawPayloadJson = rawPayloadJson;
        }
    }
}
