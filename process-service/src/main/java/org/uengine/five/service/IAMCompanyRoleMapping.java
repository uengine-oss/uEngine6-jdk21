package org.uengine.five.service;

import org.uengine.kernel.RoleMapping;
import org.uengine.util.UEngineUtil;

import java.util.Map;

/**
 * IAM 기반 RoleMapping 구현체 (resourceName 채움 전용).
 *
 * <p>애플리케이션 기동 시 {@code GlobalContext.setProperty("rolemapping.class", IAMCompanyRoleMapping.class.getName())}
 * 로 등록하면 {@code RoleMapping.create()}가 이 구현체를 생성합니다.</p>
 *
 * <p>{@code fill()} 호출 시점에만 IAMService를 통해 사용자 정보를 조회합니다.
 * RoleMapping 상위 클래스의 LRU 캐시로 endpoint당 중복 호출이 방지됩니다.</p>
 */
public class IAMCompanyRoleMapping extends RoleMapping {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    @Override
    protected String doFill() throws Exception {
        String endpoint = getEndpoint();
        if (!UEngineUtil.isNotEmpty(endpoint)) return null;

        IAMService iamService = IAMServiceFactory.getDefault();
        if (iamService == null) return endpoint;

        Map<String, Object> user = iamService.getUserById(endpoint);
        if (user == null) return endpoint;

        return asString(user.get("username"));
    }
}
