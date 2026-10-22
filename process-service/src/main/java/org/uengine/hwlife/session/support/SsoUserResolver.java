package org.uengine.hwlife.session.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.uengine.hwlife.session.config.SessionProperties;

@Component
public class SsoUserResolver {

    private final SessionProperties properties;

    public SsoUserResolver(SessionProperties properties) {
        this.properties = properties;
    }

    /**
     * 사번 식별. 우선순위: HTTP Header {@code SM_USER} → Cookie {@code SM_USER}.
     * (Proxy가 헤더로 넣는 경우 / 쿠키만 내려주는 경우 모두 대응)
     */
    public String resolveUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String name = properties.getSso().getUserHeader();
        String fromHeader = trimToNull(request.getHeader(name));
        if (fromHeader != null) {
            return fromHeader;
        }
        return trimToNull(readCookie(request, name));
    }

    public String resolveSmServerSessionId(HttpServletRequest request, String bodyValue) {
        String fromBody = trimToNull(bodyValue);
        if (fromBody != null) {
            return fromBody;
        }
        if (request == null) {
            return null;
        }
        String headerName = properties.getSso().getSessionIdHeader();
        String fromHeader = trimToNull(request.getHeader(headerName));
        if (fromHeader != null) {
            return fromHeader;
        }
        String fromParam = trimToNull(request.getParameter(headerName));
        if (fromParam != null) {
            return fromParam;
        }
        return trimToNull(readCookie(request, headerName));
    }

    private static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null || name == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
