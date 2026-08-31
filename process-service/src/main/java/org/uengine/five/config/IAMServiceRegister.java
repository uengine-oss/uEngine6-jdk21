package org.uengine.five.config;

import org.uengine.five.service.IAMServiceFactory;
import org.uengine.five.service.KeycloakIAMService;
import org.uengine.hwlife.iam.ExternalIAMService;

/**
 * IAMServiceFactory 등록 전용 유틸.
 * {@link IAMServiceInitializer} 및 기동 코드에서 공통으로 사용한다.
 */
public final class IAMServiceRegister {

    private IAMServiceRegister() {
    }

    public static void registerAll() {
        IAMServiceFactory.register("keycloak", KeycloakIAMService.getDefault());
        IAMServiceFactory.register("external", ExternalIAMService.getDefault());
    }
}
