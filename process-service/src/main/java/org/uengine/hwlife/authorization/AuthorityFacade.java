package org.uengine.hwlife.authorization;

import java.util.Collection;
import java.util.List;

/**
 * API 권한 검사 진입 계약.
 * 구현체는 {@link IamAuthorityFacadeService} 이며, IAM 조회는
 * {@link org.uengine.five.service.IAMServiceFactory}({@code iam.provider})에 위임한다.
 */
public interface AuthorityFacade {

    /**
     * 현재 요청 사용자 ID(사번 등). 없으면 401.
     */
    String requireAuthenticatedUserId();

    /**
     * 사용자가 {@code requiredAuthorities} 중 <b>하나라도</b> 보유해야 통과. 없으면 403.
     */
    void requireAnyAuthority(Collection<String> requiredAuthorities);

    /** 편의: 가변 인자. */
    default void requireAnyAuthority(String... requiredAuthorities) {
        requireAnyAuthority(requiredAuthorities == null ? List.of() : List.of(requiredAuthorities));
    }
}
