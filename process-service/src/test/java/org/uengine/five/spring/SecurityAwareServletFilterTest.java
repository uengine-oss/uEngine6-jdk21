package org.uengine.five.spring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uengine.contexts.UserContext;

class SecurityAwareServletFilterTest {
    private final SecurityAwareServletFilter filter = new SecurityAwareServletFilter();
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    private HttpServletRequest request(String token) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(token);
        return request;
    }

    private void assertEmpty() {
        assertNull(SecurityAwareServletFilter.getUserId());
        assertNull(UserContext.getThreadLocalInstance().getGroups());
        assertNull(UserContext.getThreadLocalInstance().getScopes());
    }

    @Test void nextRequestCannotReuseAuthenticatedIdentity() throws Exception {
        String token = JWT.create().withSubject("alice")
                .withClaim("groups", List.of("A"))
                .withClaim("realm_access", Map.of("roles", List.of("X")))
                .sign(Algorithm.none());
        filter.doFilter(request("Bearer " + token), response, (req, res) -> {
            assertEquals("alice", SecurityAwareServletFilter.getUserId());
            assertEquals(List.of("A"), UserContext.getThreadLocalInstance().getGroups());
            assertEquals(List.of("X"), UserContext.getThreadLocalInstance().getScopes());
        });
        assertEmpty();
        for (String header : new String[]{null, "Bearer malformed"}) {
            SecurityAwareServletFilter.setUserId("stale");
            UserContext.getThreadLocalInstance().setGroups(List.of("A"));
            filter.doFilter(request(header), response, (req, res) -> assertEmpty());
            assertEmpty();
        }
    }

    @Test void externalIdentityIsRequestLocalAndClearedOnFailure() throws Exception {
        assertThrows(ServletException.class, () -> filter.doFilter(request(null), response, (req, res) -> {
            SecurityAwareServletFilter.setUserId("external-user");
            assertEquals("external-user", UserContext.getThreadLocalInstance().getUserId());
            java.util.concurrent.atomic.AtomicReference<String> otherUser = new java.util.concurrent.atomic.AtomicReference<>();
            Thread other = new Thread(() -> otherUser.set(SecurityAwareServletFilter.getUserId()));
            other.start();
            try { other.join(); } catch (InterruptedException e) { throw new ServletException(e); }
            assertNull(otherUser.get());
            throw new ServletException("downstream failure");
        }));
        assertEmpty();
    }
}
