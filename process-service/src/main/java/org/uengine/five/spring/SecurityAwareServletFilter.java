package org.uengine.five.spring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.uengine.contexts.UserContext;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

@Component
public class SecurityAwareServletFilter implements Filter {

    static String userId;

    static public String getUserId() {
        return userId;
    }

    /** ESB 등 JWT 없는 경로에서 업무 API 권한 검사에 쓸 사용자 ID를 설정한다. */
    static public void setUserId(String userId) {
        SecurityAwareServletFilter.userId = userId;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String accessToken = req.getHeader("Authorization");

        if (accessToken != null) {
            try {
                res.setHeader("Access-Control-Allow-Origin", "*");
                res.setHeader("Access-Control-Allow-Credentials", "true");
                res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
                res.setHeader("Access-Control-Max-Age", "3600");
                res.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With, remember-me");

                accessToken = accessToken.split("Bearer ")[1];
                DecodedJWT decodedJWT = JWT.decode(accessToken);

                List<String> groups = decodedJWT.getClaim("groups").asList(String.class);

                // BPM workitem endpoint 는 데모/업무 계정의 preferred_username 기준으로 생성된다.
                // email 을 우선하면 endpoint=유, token user=you@uengine.org 처럼 완료 권한 검사가 어긋난다.
                // preferred_username 이 없을 때만 email/sub 로 fallback 한다.
                String userId = decodedJWT.getClaim("preferred_username").asString();
                if (userId == null || userId.isEmpty()) {
                    userId = decodedJWT.getClaim("email").asString();
                }
                if (userId == null || userId.isEmpty()) {
                    userId = decodedJWT.getClaim("sub").asString();
                }

                List<String> roles = null;
                try {
                    java.util.Map<String, Object> realmAccess = decodedJWT.getClaim("realm_access").asMap();
                    if (realmAccess != null) {
                        Object r = realmAccess.get("roles");
                        if (r instanceof List) roles = (List<String>) r;
                    }
                } catch (Exception ignore) {}

                // 토큰에서 userId 추출 실패한 경우 정적 캐시를 null 로 덮어쓰지 않는다.
                // (정적 필드라 다른 정상 요청이 셋업한 값까지 잃어버려 race condition + 모든 후속 process start 가 endpoint=null 로 저장됨)
                if (userId != null && !userId.isEmpty()) {
                    SecurityAwareServletFilter.userId = userId;
                    UserContext.getThreadLocalInstance().setUserId(userId);
                } else {
                    System.err.println("[SecurityAwareServletFilter] JWT 에서 userId 추출 실패. claim 후보(preferred_username/email/sub) 모두 비어있음. Keycloak realm/client mapper 설정을 확인하세요.");
                }
                UserContext.getThreadLocalInstance().setScopes(roles);
                UserContext.getThreadLocalInstance().setGroups(groups);
            } catch (Exception e) {
                System.out.println("Error when to parse accesstoken: " + e.getMessage());
            }
        }

        chain.doFilter(req, res);

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // TODO Auto-generated method stub

    }

    @Override
    public void destroy() {
        // TODO Auto-generated method stub

    }

    // other methods
}
