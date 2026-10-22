package org.uengine.hwlife.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "uengine.session")
public class SessionProperties {

    private int idleTimeoutMinutes = 30;
    private int idleTimeoutMaxMinutes = 30;
    private int extendPromptSeconds = 60;
    private int jwtTtlHours = 8;
    private String jwtSecret = "uengine-session-dev-secret-change-me";
    private final Sso sso = new Sso();

    public int getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }

    public int getIdleTimeoutMaxMinutes() {
        return idleTimeoutMaxMinutes;
    }

    public void setIdleTimeoutMaxMinutes(int idleTimeoutMaxMinutes) {
        this.idleTimeoutMaxMinutes = idleTimeoutMaxMinutes;
    }

    public int getExtendPromptSeconds() {
        return extendPromptSeconds;
    }

    public void setExtendPromptSeconds(int extendPromptSeconds) {
        this.extendPromptSeconds = extendPromptSeconds;
    }

    public int getJwtTtlHours() {
        return jwtTtlHours;
    }

    public void setJwtTtlHours(int jwtTtlHours) {
        this.jwtTtlHours = jwtTtlHours;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Sso getSso() {
        return sso;
    }

    public static class Sso {
        private String userHeader = "SM_USER";
        private String sessionCookie = "SMSESSION";
        private String sessionIdHeader = "SM_SERVERSESSIONID";
        private String logoutCookieValue = "LOGGEDOFF";

        public String getUserHeader() {
            return userHeader;
        }

        public void setUserHeader(String userHeader) {
            this.userHeader = userHeader;
        }

        public String getSessionCookie() {
            return sessionCookie;
        }

        public void setSessionCookie(String sessionCookie) {
            this.sessionCookie = sessionCookie;
        }

        public String getSessionIdHeader() {
            return sessionIdHeader;
        }

        public void setSessionIdHeader(String sessionIdHeader) {
            this.sessionIdHeader = sessionIdHeader;
        }

        public String getLogoutCookieValue() {
            return logoutCookieValue;
        }

        public void setLogoutCookieValue(String logoutCookieValue) {
            this.logoutCookieValue = logoutCookieValue;
        }
    }
}
