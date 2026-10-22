package org.uengine.hwlife.session.support;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.uengine.hwlife.session.config.SessionProperties;
import org.uengine.hwlife.session.entity.UserSessionEntity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

@Component
public class JwtSessionTokenService {

    private final SessionProperties properties;

    public JwtSessionTokenService(SessionProperties properties) {
        this.properties = properties;
    }

    public String issue(UserSessionEntity session, Map<String, Object> userClaims) {
        Algorithm algorithm = Algorithm.HMAC256(properties.getJwtSecret());
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getJwtTtlHours() * 3600L);

        var builder = JWT.create()
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(exp))
                .withClaim("sid", session.getSessionToken())
                .withClaim("ltp", session.getLoginType())
                .withClaim("uid", session.getUserId())
                .withClaim("unm", session.getUserName());

        if (userClaims != null) {
            for (Map.Entry<String, Object> e : userClaims.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                if (e.getValue() instanceof String s) {
                    builder.withClaim(e.getKey(), s);
                } else if (e.getValue() instanceof Integer i) {
                    builder.withClaim(e.getKey(), i);
                } else if (e.getValue() instanceof Long l) {
                    builder.withClaim(e.getKey(), l);
                } else if (e.getValue() instanceof Boolean b) {
                    builder.withClaim(e.getKey(), b);
                } else {
                    builder.withClaim(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        }
        return builder.sign(algorithm);
    }

    public DecodedJWT verify(String token) {
        Algorithm algorithm = Algorithm.HMAC256(properties.getJwtSecret());
        return JWT.require(algorithm).build().verify(token);
    }

    public String extractSessionToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        String raw = accessToken.trim();
        if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
            raw = raw.substring(7).trim();
        }
        DecodedJWT jwt = verify(raw);
        return jwt.getClaim("sid").asString();
    }

    public Map<String, Object> claimsAsMap(DecodedJWT jwt) {
        Map<String, Object> map = new HashMap<>();
        jwt.getClaims().forEach((k, v) -> {
            if (v == null) {
                return;
            }
            Object val = v.as(Object.class);
            if (val != null) {
                map.put(k, val);
            }
        });
        return map;
    }
}
