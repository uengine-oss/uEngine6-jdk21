package org.uengine.five.messaging.polling;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * EventSource 는 표준상 커스텀 HTTP 헤더를 실을 수 없다. {@code /events/stream} 에
 * 쿼리파라미터 {@code access_token} 으로 전달된 Keycloak 토큰을 {@code Authorization: Bearer}
 * 헤더로 변환해 뒤의 Security 체인이 기존과 동일하게 검증할 수 있도록 한다.
 *
 * <p>범위 제한: {@code /events/} 경로에서만 동작. 다른 경로에서 쿼리파라미터로 토큰 노출을
 * 허용하면 보안 위험이 있어 의도적으로 좁게 제한.
 */
public class SseAccessTokenQueryFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/events/") && request.getHeader("Authorization") == null) {
            String token = request.getParameter("access_token");
            if (token != null && !token.isEmpty()) {
                final String bearer = "Bearer " + token;
                request = new HttpServletRequestWrapper(request) {
                    @Override
                    public String getHeader(String name) {
                        if ("Authorization".equalsIgnoreCase(name)) return bearer;
                        return super.getHeader(name);
                    }
                };
            }
        }
        chain.doFilter(request, response);
    }
}
