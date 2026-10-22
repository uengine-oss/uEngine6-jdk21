package org.uengine.hwlife.session.support;

import java.util.Date;

import org.springframework.stereotype.Component;
import org.uengine.hwlife.session.config.SessionProperties;
import org.uengine.hwlife.session.entity.UserSessionEntity;

@Component
public class SessionIdlePolicy {

    private final SessionProperties properties;

    public SessionIdlePolicy(SessionProperties properties) {
        this.properties = properties;
    }

    public int clampIdleMinutes(Integer requested) {
        int max = Math.max(1, properties.getIdleTimeoutMaxMinutes());
        int def = Math.min(Math.max(1, properties.getIdleTimeoutMinutes()), max);
        if (requested == null || requested < 1) {
            return def;
        }
        return Math.min(requested, max);
    }

    public Date expiresAt(UserSessionEntity session) {
        if (session == null || session.getLastSeenAt() == null) {
            return null;
        }
        int minutes = session.getIdleTimeoutMinutes() != null
                ? session.getIdleTimeoutMinutes()
                : properties.getIdleTimeoutMinutes();
        return new Date(session.getLastSeenAt().getTime() + minutes * 60_000L);
    }

    public long remainingSeconds(UserSessionEntity session, Date now) {
        Date exp = expiresAt(session);
        if (exp == null || now == null) {
            return 0L;
        }
        return Math.max(0L, (exp.getTime() - now.getTime()) / 1000L);
    }

    public boolean isExpired(UserSessionEntity session, Date now) {
        return remainingSeconds(session, now) <= 0L;
    }

    public boolean shouldPromptExtend(UserSessionEntity session, Date now) {
        long remaining = remainingSeconds(session, now);
        return remaining > 0L && remaining <= properties.getExtendPromptSeconds();
    }
}
