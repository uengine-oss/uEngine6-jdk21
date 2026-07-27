package org.uengine.hwlife.esbclient.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ESB 인바운드 요청 봉투 {@code { "header": {...}, "payload": {...} }} 를 풀어
 * {@code payload} 만 {@code @RequestBody} DTO 로 바인딩한다.
 *
 * <p>적용 대상: {@code org.uengine.hwlife} 패키지의 {@code @RequestBody} (단, {@link String} 제외).
 * {@code payload} 필드가 없으면 본문을 그대로 통과한다(하위 호환).</p>
 *
 * <p>요청 header 는 {@link #HEADER_ATTR} request attribute 에 보관하며
 * {@link #currentHeader()} 로 조회한다.</p>
 *
 * <p>on/off: {@code esb.inbound-unwrap.enabled} (기본 {@code true}).</p>
 */
@ControllerAdvice
@ConditionalOnProperty(name = "esb.inbound-unwrap.enabled", havingValue = "true", matchIfMissing = true)
public class EsbRequestBodyAdvice implements RequestBodyAdvice {

    public static final String HEADER_ATTR = "esb.header";

    private static final String HWLIFE_PACKAGE_PREFIX = "org.uengine.hwlife";

    private final ObjectMapper objectMapper;

    public EsbRequestBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 현재 요청에 저장된 ESB header. 없거나 요청 스코프 밖이면 {@code null}.
     */
    public static EsbCommonHeader currentHeader() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        Object value = attrs.getAttribute(HEADER_ATTR, RequestAttributes.SCOPE_REQUEST);
        return value instanceof EsbCommonHeader ? (EsbCommonHeader) value : null;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (!parameter.hasParameterAnnotation(RequestBody.class)) {
            return false;
        }
        if (String.class.equals(parameter.getParameterType())) {
            return false;
        }
        Package pkg = parameter.getContainingClass().getPackage();
        return pkg != null && pkg.getName().startsWith(HWLIFE_PACKAGE_PREFIX);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] body = StreamUtils.copyToByteArray(inputMessage.getBody());
        if (body.length == 0) {
            return replay(inputMessage, body);
        }

        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.has("payload")) {
            return replay(inputMessage, body);
        }

        if (root.hasNonNull("header")) {
            EsbCommonHeader header = objectMapper.treeToValue(root.get("header"), EsbCommonHeader.class);
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                attrs.setAttribute(HEADER_ATTR, header, RequestAttributes.SCOPE_REQUEST);
            }
        }

        JsonNode payload = root.get("payload");
        byte[] payloadBytes = payload == null || payload.isNull()
                ? "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                : objectMapper.writeValueAsBytes(payload);
        return replay(inputMessage, payloadBytes);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private static HttpInputMessage replay(HttpInputMessage original, byte[] body) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return original.getHeaders();
            }
        };
    }
}
