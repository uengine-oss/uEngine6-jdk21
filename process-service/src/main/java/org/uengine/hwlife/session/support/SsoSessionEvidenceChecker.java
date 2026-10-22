package org.uengine.hwlife.session.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.uengine.hwlife.session.config.SessionProperties;
import org.uengine.hwlife.session.entity.UserSessionEntity;

/**
 * SSO 세션 증거(SMSESSION 쿠키 · SM_USER 헤더)로 서비스 세션 유효성을 판정한다.
 */
@Component
public class SsoSessionEvidenceChecker {

    private final SessionProperties properties;
    private final SsoUserResolver userResolver;

    public SsoSessionEvidenceChecker(SessionProperties properties, SsoUserResolver userResolver) {
        this.properties = properties;
        this.userResolver = userResolver;
    }

    /**
     * @return null 이면 유효, 아니면 종료 reason_code
     */
    public String invalidReason(UserSessionEntity session, HttpServletRequest request) {
        if (session == null || !UserSessionEntity.LOGIN_TYPE_SSO.equals(session.getLoginType())) {
            return null;
        }
        String cookieName = properties.getSso().getSessionCookie();
        String logoutValue = properties.getSso().getLogoutCookieValue();
        String smSession = readCookie(request, cookieName);
        if (smSession == null || smSession.isBlank()
                || (logoutValue != null && logoutValue.equalsIgnoreCase(smSession.trim()))) {
            return "SSO_SESSION_ENDED";
        }
        String headerUser = userResolver.resolveUserId(request);
        if (headerUser == null) {
            return "SSO_SESSION_ENDED";
        }
        if (session.getUserId() != null && !session.getUserId().equals(headerUser)) {
            return "SSO_SESSION_ENDED";
        }
        return null;
    }

    private static String readCookie(HttpServletRequest request, String name) {
        if (request == null || name == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
