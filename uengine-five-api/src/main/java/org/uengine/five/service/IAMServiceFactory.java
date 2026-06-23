package org.uengine.five.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.uengine.kernel.GlobalContext;
import org.uengine.util.UEngineUtil;

/**
 * IAMService 정적 접근 팩토리 (Registry 패턴).
 *
 * <p>
 * RoleResolutionContext가 직렬화되어야 하는 구조 특성상, Spring DI 대신
 * 런타임에 정적으로 IAM 구현을 선택하는 방식(Strategy + Registry)을 사용합니다.
 * </p>
 *
 * <p>
 * 사용법: 애플리케이션 기동 시점에 {@link #register(String, IAMService)}로 구현체를 등록한 뒤
 * {@link #getDefault()} / {@link #get(String)}으로 어디서든 접근합니다.
 * 구현체는 코어/API 레이어가 아닌 애플리케이션(process-service 등) 레이어에서 등록합니다.
 * </p>
 *
 * <p>선택 기준 (getDefault 호출 시):</p>
 * <ul>
 *   <li>환경변수: IAM_PROVIDER</li>
 *   <li>GlobalContext property: iam.provider</li>
 *   <li>기본값: keycloak</li>
 * </ul>
 */
public final class IAMServiceFactory {

    private static final String DEFAULT_PROVIDER_ID = "keycloak";

    private static final Map<String, IAMService> REGISTRY = new ConcurrentHashMap<>();

    private IAMServiceFactory() {
    }

    /**
     * IAMService 구현체를 등록합니다.
     * 애플리케이션 기동 시점(main() 등)에 호출해야 합니다.
     *
     * @param providerId 공급자 식별자 (예: "keycloak", "external")
     * @param service    IAMService 구현체
     */
    public static void register(String providerId, IAMService service) {
        if (providerId == null || service == null) return;
        REGISTRY.put(providerId.trim().toLowerCase(), service);
    }

    /**
     * 환경변수(IAM_PROVIDER) 또는 설정(iam.provider)에 따라 기본 IAMService를 반환합니다.
     *
     * @throws IllegalStateException 등록된 구현체가 없을 경우
     */
    public static IAMService getDefault() {
        String providerId = System.getenv("IAM_PROVIDER");
        if (!UEngineUtil.isNotEmpty(providerId)) {
            providerId = GlobalContext.getPropertyString("iam.provider", DEFAULT_PROVIDER_ID);
        }
        return get(providerId);
    }

    /**
     * providerId로 등록된 IAMService를 반환합니다.
     *
     * @throws IllegalStateException 등록된 구현체가 없을 경우
     */
    public static IAMService get(String providerId) {
        final String normalized = UEngineUtil.isNotEmpty(providerId)
                ? providerId.trim().toLowerCase()
                : DEFAULT_PROVIDER_ID;
        IAMService service = REGISTRY.get(normalized);
        if (service == null) {
            throw new IllegalStateException(
                    "No IAMService registered for provider '" + normalized + "'. " +
                    "Call IAMServiceFactory.register(providerId, service) at application startup.");
        }
        return service;
    }
}
