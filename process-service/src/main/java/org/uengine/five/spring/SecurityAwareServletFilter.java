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

    static public String getUserId() {
        return UserContext.getThreadLocalInstance().getUserId();
    }

    /** ESB 등 JWT 없는 경로에서 업무 API 권한 검사에 쓸 사용자 ID를 설정한다. */
    static public void setUserId(String userId) {
        UserContext.getThreadLocalInstance().setUserId(userId);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        clearUserContext();
        try {
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

                    // 공급자별 토큰 식별자를 email → preferred_username → sub 순으로 확인한다.
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

                    if (userId != null && !userId.isEmpty()) {
                        UserContext.getThreadLocalInstance().setUserId(userId);
                    } else {
                        System.err.println("[SecurityAwareServletFilter] JWT 에서 userId 추출 실패. claim 후보(email/preferred_username/sub) 모두 비어있음. 인증 공급자의 사용자 식별자 설정을 확인하세요.");
                    }
                    UserContext.getThreadLocalInstance().setScopes(roles);
                    UserContext.getThreadLocalInstance().setGroups(groups);
                } catch (Exception e) {
                    System.out.println("Error when to parse accesstoken: " + e.getMessage());
                }
            }

            chain.doFilter(req, res);
        } finally {
            clearUserContext();
        }
    }

    private static void clearUserContext() {
        UserContext context = UserContext.getThreadLocalInstance();
        context.setUserId(null);
        context.setGroups(null);
        context.setScopes(null);
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
