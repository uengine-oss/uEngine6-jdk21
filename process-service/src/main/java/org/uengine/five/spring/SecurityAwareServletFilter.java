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
    };

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

                // Keycloak Realm 설정에 따라 email mapper 가 클라이언트 scope 에 포함돼 있지 않을 수 있다.
                // 그 경우 email claim 이 null 이라 SecurityAwareServletFilter.userId = null 로 덮어써져
                // HumanActivity 의 endpoint fallback 이 setEndpoint(null) 로 끝나고 worklist 가 endpoint 없이 저장된다.
                // → email → preferred_username → sub 순으로 fallback.
                String userId = decodedJWT.getClaim("email").asString();
                if (userId == null || userId.isEmpty()) {
                    userId = decodedJWT.getClaim("preferred_username").asString();
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
                    System.err.println("[SecurityAwareServletFilter] JWT 에서 userId 추출 실패. claim 후보(email/preferred_username/sub) 모두 비어있음. Keycloak realm/client mapper 설정을 확인하세요.");
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
