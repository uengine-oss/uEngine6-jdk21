package org.uengine.hwlife.authorization;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.service.IAMServiceFactory;
import org.uengine.five.spring.SecurityAwareServletFilter;

/**
 * HTTP 세션 사용자 + {@link IAMServiceFactory} 기반 권한 검사.
 * <p>
 * IAM 공급자 선택은 {@code IAM_PROVIDER} / {@code iam.provider}
 * (external · keycloak 등) — {@link AuthorityFacade} 와 이중 설정하지 않는다.
 * </p>
 */
@Service
public class IamAuthorityFacadeService implements AuthorityFacade {

    /** 사내 External IAM 권한 거부 reason 코드. */
    public static final String DENIED_REASON_CODE = "LBM090002";

    @Override
    public String requireAuthenticatedUserId() {
        String userId = trimToNull(UserContext.getThreadLocalInstance().getUserId());
        if (userId == null) {
            userId = trimToNull(SecurityAwareServletFilter.getUserId());
        }
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
        }
        return userId;
    }

    @Override
    public void requireAnyAuthority(Collection<String> requiredAuthorities) {
        if (requiredAuthorities == null || requiredAuthorities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, DENIED_REASON_CODE);
        }
        String userId = requireAuthenticatedUserId();
        List<String> authorities;
        try {
            authorities = IAMServiceFactory.getDefault().getUserScopes(userId);
        } catch (Exception e) {
            authorities = List.of();
        }
        if (authorities == null) {
            authorities = List.of();
        }
        boolean allowed = authorities.stream().anyMatch(requiredAuthorities::contains);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, DENIED_REASON_CODE);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
