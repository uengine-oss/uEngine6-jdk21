package org.uengine.five.spring;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.hwlife.session.entity.UserSessionEntity;
import org.uengine.hwlife.session.repository.UserSessionRepository;
import org.uengine.hwlife.session.support.JwtSessionTokenService;
import org.uengine.hwlife.session.support.SessionIdlePolicy;
import org.uengine.hwlife.session.support.SsoSessionEvidenceChecker;
import org.uengine.kernel.GlobalContext;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SecurityAwareServletFilter implements Filter {

    static String userId;

    private static final String FILE_EXECUTION_ACTOR = SecurityAwareServletFilter.class.getName() + ".fileExecutionActor";
    private static final HttpClient IDENTITY_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper IDENTITY_JSON = new ObjectMapper();

    private final JwtSessionTokenService jwtSessionTokenService;
    private final UserSessionRepository userSessionRepository;
    private final SessionIdlePolicy sessionIdlePolicy;
    private final SsoSessionEvidenceChecker ssoSessionEvidenceChecker;

    public SecurityAwareServletFilter(
            JwtSessionTokenService jwtSessionTokenService,
            UserSessionRepository userSessionRepository,
            SessionIdlePolicy sessionIdlePolicy,
            SsoSessionEvidenceChecker ssoSessionEvidenceChecker) {
        this.jwtSessionTokenService = jwtSessionTokenService;
        this.userSessionRepository = userSessionRepository;
        this.sessionIdlePolicy = sessionIdlePolicy;
        this.ssoSessionEvidenceChecker = ssoSessionEvidenceChecker;
    }

    static public String getUserId() {
        return userId;
    }

    /** ESB 등 JWT 없는 경로에서 업무 API 권한 검사에 쓸 사용자 ID를 설정한다. */
    static public void setUserId(String userId) {
        SecurityAwareServletFilter.userId = userId;
    }

    public static String getVerifiedFileExecutionActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes current)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        HttpServletRequest request = current.getRequest();
        Object identity = request.getAttribute(FILE_EXECUTION_ACTOR);
        if (identity instanceof String actor) return actor;
        if (identity instanceof Supplier<?> verification && verification.get() instanceof String actor) {
            request.setAttribute(FILE_EXECUTION_ACTOR, actor);
            return actor;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String authorization = req.getHeader("Authorization");

        if (authorization != null && !authorization.isBlank()) {
            try {
                res.setHeader("Access-Control-Allow-Origin", "*");
                res.setHeader("Access-Control-Allow-Credentials", "true");
                res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
                res.setHeader("Access-Control-Max-Age", "3600");
                res.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With, remember-me");

                String accessToken = extractBearerToken(authorization);
                if (accessToken == null) {
                    chain.doFilter(req, res);
                    return;
                }

                DecodedJWT peek = JWT.decode(accessToken);
                if (isSessionJwt(peek)) {
                    if (!applySessionJwt(req, res, accessToken)) {
                        return;
                    }
                } else {
                    req.setAttribute(FILE_EXECUTION_ACTOR, (Supplier<String>) () -> verifyKeycloakFileActor(accessToken));
                    applyKeycloakJwt(peek);
                }
            } catch (Exception e) {
                System.out.println("Error when to parse accesstoken: " + e.getMessage());
            }
        }

        chain.doFilter(req, res);
    }

    /**
     * Session JWT(sid+uid+ltp) 검증·ACTIVE 확인 후 UserContext 설정.
     * @return false 이면 이미 401 응답을 썼으므로 chain을 이어가지 않음
     */
    private boolean applySessionJwt(HttpServletRequest req, HttpServletResponse res, String accessToken)
            throws IOException {
        try {
            DecodedJWT verified = jwtSessionTokenService.verify(accessToken);
            String uid = blankToNull(verified.getClaim("uid").asString());
            String sid = blankToNull(verified.getClaim("sid").asString());
            if (uid == null || sid == null) {
                return rejectSessionAuth(req, res, "SESSION_CLAIM_MISSING");
            }

            UserSessionEntity session = userSessionRepository.findBySessionToken(sid).orElse(null);
            if (session == null || !UserSessionEntity.STATUS_ACTIVE.equals(session.getStatus())) {
                return rejectSessionAuth(req, res, "SESSION_NOT_ACTIVE");
            }

            req.setAttribute(FILE_EXECUTION_ACTOR,
                    (Supplier<String>) () -> verifySessionFileActor(req, verified, session, uid));
            SecurityAwareServletFilter.userId = uid;
            UserContext.getThreadLocalInstance().setUserId(uid);
            return true;
        } catch (Exception e) {
            System.out.println("[SecurityAwareServletFilter] Session JWT verify failed: " + e.getMessage());
            return rejectSessionAuth(req, res, "INVALID_TOKEN");
        }
    }

    private String verifySessionFileActor(HttpServletRequest request, DecodedJWT verified,
            UserSessionEntity session, String uid) {
        if (!uid.equals(session.getUserId())
                || !Objects.equals(verified.getClaim("ltp").asString(), session.getLoginType())
                || sessionIdlePolicy.isExpired(session, new Date())
                || ssoSessionEvidenceChecker.invalidReason(session, request) != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return "session:" + uid;
    }

    private static String verifyKeycloakFileActor(String accessToken) {
        String server = System.getenv().getOrDefault("KEYCLOAK_URI",
                GlobalContext.getPropertyString("keycloak.uri", "http://localhost:8080"));
        String realm = System.getenv().getOrDefault("KEYCLOAK_REALM",
                GlobalContext.getPropertyString("keycloak.realm", "uengine"));
        if (!realm.matches("[a-zA-Z0-9_.-]+")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid file execution identity configuration");
        }

        try {
            URI endpoint = URI.create(server.replaceAll("/+$", "") + "/realms/" + realm + "/protocol/openid-connect/userinfo");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = IDENTITY_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode subject = IDENTITY_JSON.readTree(response.body()).path("sub");
                if (subject.isTextual() && !subject.asText().isBlank()) return "keycloak:" + realm + ":" + subject.asText();
            } else if (response.statusCode() >= 500 || response.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File execution identity provider unavailable");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File execution identity verification interrupted", interrupted);
        } catch (IOException | IllegalArgumentException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File execution identity provider unavailable", unavailable);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private boolean rejectSessionAuth(HttpServletRequest req, HttpServletResponse res, String reason)
            throws IOException {
        if (isSessionApiPath(req)) {
            // /session/* 는 body 토큰으로 자체 처리 — 필터에서 막지 않음
            return true;
        }
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setHeader("X-Auth-Failure", reason);
        return false;
    }

    private void applyKeycloakJwt(DecodedJWT decodedJWT) {
        List<String> groups = decodedJWT.getClaim("groups").asList(String.class);

        // Keycloak Realm 설정에 따라 email mapper 가 클라이언트 scope 에 포함돼 있지 않을 수 있다.
        // 그 경우 email claim 이 null 이라 SecurityAwareServletFilter.userId = null 로 덮어써져
        // HumanActivity 의 endpoint fallback 이 setEndpoint(null) 로 끝나고 worklist 가 endpoint 없이 저장된다.
        // → email → preferred_username → sub 순으로 fallback.
        String resolvedUserId = decodedJWT.getClaim("email").asString();
        if (resolvedUserId == null || resolvedUserId.isEmpty()) {
            resolvedUserId = decodedJWT.getClaim("preferred_username").asString();
        }
        if (resolvedUserId == null || resolvedUserId.isEmpty()) {
            resolvedUserId = decodedJWT.getClaim("sub").asString();
        }

        List<String> roles = null;
        try {
            java.util.Map<String, Object> realmAccess = decodedJWT.getClaim("realm_access").asMap();
            if (realmAccess != null) {
                Object r = realmAccess.get("roles");
                if (r instanceof List) {
                    roles = (List<String>) r;
                }
            }
        } catch (Exception ignore) {
        }

        // 토큰에서 userId 추출 실패한 경우 정적 캐시를 null 로 덮어쓰지 않는다.
        // (정적 필드라 다른 정상 요청이 셋업한 값까지 잃어버려 race condition + 모든 후속 process start 가 endpoint=null 로 저장됨)
        if (resolvedUserId != null && !resolvedUserId.isEmpty()) {
            SecurityAwareServletFilter.userId = resolvedUserId;
            UserContext.getThreadLocalInstance().setUserId(resolvedUserId);
        } else {
            System.err.println(
                    "[SecurityAwareServletFilter] JWT 에서 userId 추출 실패. claim 후보(email/preferred_username/sub) 모두 비어있음. Keycloak realm/client mapper 설정을 확인하세요.");
        }
        UserContext.getThreadLocalInstance().setScopes(roles);
        UserContext.getThreadLocalInstance().setGroups(groups);
    }

    private static boolean isSessionJwt(DecodedJWT jwt) {
        return blankToNull(jwt.getClaim("sid").asString()) != null
                && blankToNull(jwt.getClaim("uid").asString()) != null
                && blankToNull(jwt.getClaim("ltp").asString()) != null;
    }

    private static boolean isSessionApiPath(HttpServletRequest req) {
        String path = req.getServletPath();
        if (path == null || path.isEmpty()) {
            path = req.getRequestURI();
            String ctx = req.getContextPath();
            if (path != null && ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
                path = path.substring(ctx.length());
            }
        }
        if (path == null) {
            return false;
        }
        return path.equals("/session") || path.startsWith("/session/");
    }

    private static String extractBearerToken(String authorization) {
        String raw = authorization.trim();
        if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = raw.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
