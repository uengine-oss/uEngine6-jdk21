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

/**
 * ESB 인바운드 응답을 {@code { "header": {...}, "payload": {...} }} 봉투로 감싼다.
 *
 * <p>적용 대상: {@code org.uengine.hwlife} 패키지의 {@code @ResponseBody}/REST 반환값.
 * 요청 시 {@link EsbRequestBodyAdvice} 가 header 를 저장한 경우에만 감싸며,
 * header 가 없으면 본문을 그대로 통과한다(하위 호환).</p>
 *
 * <p>이미 {@link EsbResponse} 이면 재포장하지 않는다.
 * 기본은 {@link EsbEnvelope#success}. 시스템 실패는 {@link #markFailed(String)} 후 payload 를 반환한다.
 * 업무 실패 상세는 payload 에 담고 header 는 성공({@code prcsRsltDvsnCode=0})으로 둔다.</p>
 *
 * <p>on/off: {@code esb.outbound-wrap.enabled} (기본 {@code true}).</p>
 */
@ControllerAdvice
@ConditionalOnProperty(name = "esb.outbound-wrap.enabled", havingValue = "true", matchIfMissing = true)
public class EsbResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final String FAILED_REASON_ATTR = "esb.failedReason";
    private static final String HWLIFE_PACKAGE_PREFIX = "org.uengine.hwlife";

    /**
     * 현재 요청 응답을 시스템 실패({@code prcsRsltDvsnCode=1}) 로 표시한다.
     */
    public static void markFailed(String reason) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        attrs.setAttribute(FAILED_REASON_ATTR, reason != null ? reason : "", RequestAttributes.SCOPE_REQUEST);
    }

    static boolean isMarkedFailed() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }
        return attrs.getAttribute(FAILED_REASON_ATTR, RequestAttributes.SCOPE_REQUEST) != null;
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
        if (body instanceof EsbResponse) {
            return body;
        }
        if (body instanceof CharSequence || body instanceof byte[]) {
            return body;
        }

        EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
        if (header == null) {
            return body;
        }

        if (isMarkedFailed()) {
            return EsbEnvelope.failed(header, body);
        }
        return EsbEnvelope.success(header, body);
    }
}
