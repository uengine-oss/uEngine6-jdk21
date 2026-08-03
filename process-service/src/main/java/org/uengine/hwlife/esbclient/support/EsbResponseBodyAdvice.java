package org.uengine.hwlife.esbclient.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.dto.EsbResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ESB 인바운드 응답을 {@code { "header": {...}, "payload": {...} }} 봉투로 감싼다.
 *
 * <p>적용 대상: {@code org.uengine.hwlife} 패키지의 {@code @ResponseBody}/REST 반환값.
 * 요청 시 {@link EsbRequestBodyAdvice} 가 header 를 저장한 경우에만 감싸며,
 * header 가 없으면 본문을 그대로 통과한다(하위 호환).</p>
 *
 * <p>이미 {@link EsbResponse} 이면 재포장하지 않는다.
 * 업무 실패로 {@code prcsRsltDvsnCode=1} 을 내려야 하면
 * {@link #markFailed(String)} 후 payload DTO 를 반환한다.</p>
 *
 * <p>응답 본문을 {@link EsbResponse} POJO 가 아니라 {@link ObjectNode} 로 반환한다.
 * Spring MVC ObjectMapper 가 NON_NULL 이어도 JsonNode 트리는 null 키를 유지한다.</p>
 *
 * <p>on/off: {@code esb.outbound-wrap.enabled} (기본 {@code true}).</p>
 */
@ControllerAdvice
@ConditionalOnProperty(name = "esb.outbound-wrap.enabled", havingValue = "true", matchIfMissing = true)
public class EsbResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    public static final String FAILED_REASON_ATTR = "esb.failedReason";

    private static final String HWLIFE_PACKAGE_PREFIX = "org.uengine.hwlife";

    /** payload: null 필드 키 유지 */
    private final ObjectMapper payloadMapper;

    /** header: null 필드는 생략(기존 EsbCommonHeader NON_NULL 관례) */
    private final ObjectMapper headerMapper;

    public EsbResponseBodyAdvice(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper is required");
        }
        // Spring/Hateoas mixin(NON_NULL on Object) 을 물려받지 않도록 신규 mapper 사용
        this.payloadMapper = createMapper(JsonInclude.Include.ALWAYS);
        this.headerMapper = createMapper(JsonInclude.Include.NON_NULL);
    }

    private static ObjectMapper createMapper(JsonInclude.Include inclusion) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(inclusion, inclusion));
        return mapper;
    }

    /**
     * 현재 요청 응답을 ESB 실패({@code prcsRsltDvsnCode=1}) 로 표시한다.
     * {@link #beforeBodyWrite} 가 payload 는 그대로 두고 header 만 실패로 채운다.
     */
    public static void markFailed(String reason) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        attrs.setAttribute(FAILED_REASON_ATTR, reason != null ? reason : "", RequestAttributes.SCOPE_REQUEST);
    }

    static String currentFailedReason() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        Object value = attrs.getAttribute(FAILED_REASON_ATTR, RequestAttributes.SCOPE_REQUEST);
        return value instanceof String ? (String) value : null;
    }

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        Package pkg = returnType.getContainingClass().getPackage();
        return pkg != null && pkg.getName().startsWith(HWLIFE_PACKAGE_PREFIX);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof EsbResponse || body instanceof JsonNode) {
            return body;
        }
        if (body instanceof CharSequence || body instanceof byte[]) {
            return body;
        }

        EsbCommonHeader requestHeader = EsbRequestBodyAdvice.currentHeader();
        if (requestHeader == null) {
            return body;
        }

        String failedReason = currentFailedReason();
        EsbResponse<?> wrapped = failedReason != null
                ? EsbEnvelope.failed(requestHeader, body, failedReason.isBlank() ? null : failedReason)
                : EsbEnvelope.success(requestHeader, body);

        // POJO 재직렬화(NON_NULL mixin)를 피하고, 완성된 JSON 트리를 그대로 HTTP 본문으로 반환
        return toEnvelopeNode(wrapped.getHeader(), wrapped.getPayload());
    }

    ObjectNode toEnvelopeNode(EsbCommonHeader header, Object payload) {
        ObjectNode envelope = payloadMapper.createObjectNode();
        envelope.set("header", headerMapper.valueToTree(header != null ? header : new EsbCommonHeader()));
        envelope.set("payload", toAlwaysIncludePayload(payload));
        return envelope;
    }

    /** DTO 를 null 필드 키까지 포함한 JSON 트리로 변환한다({@code "field": null}). */
    JsonNode toAlwaysIncludePayload(Object body) {
        if (body == null) {
            return payloadMapper.nullNode();
        }
        if (body instanceof JsonNode) {
            return (JsonNode) body;
        }
        return payloadMapper.valueToTree(body);
    }
}
