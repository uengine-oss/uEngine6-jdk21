package org.uengine.five.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.uengine.five.service.IAMServiceFactory;
import org.uengine.five.service.KeycloakIAMService;
import org.uengine.hwlife.iam.ExternalIAMService;

class IAMServiceRegisterTest {
    @Test
    void externalModeDoesNotInitializeKeycloak() {
        try (MockedStatic<IAMServiceFactory> factory = mockStatic(IAMServiceFactory.class);
                MockedStatic<KeycloakIAMService> keycloak = mockStatic(KeycloakIAMService.class)) {
            factory.when(IAMServiceFactory::getDefaultProviderId).thenReturn("external");

            IAMServiceRegister.registerAll();

            keycloak.verifyNoInteractions();
            factory.verify(() -> IAMServiceFactory.register("external", ExternalIAMService.getDefault()));
        }
    }

    @Test
    void keycloakModeStillRegistersItsProvider() {
        KeycloakIAMService provider = mock(KeycloakIAMService.class);
        try (MockedStatic<IAMServiceFactory> factory = mockStatic(IAMServiceFactory.class);
                MockedStatic<KeycloakIAMService> keycloak = mockStatic(KeycloakIAMService.class)) {
            factory.when(IAMServiceFactory::getDefaultProviderId).thenReturn("keycloak");
            keycloak.when(KeycloakIAMService::getDefault).thenReturn(provider);

            IAMServiceRegister.registerAll();

            keycloak.verify(KeycloakIAMService::getDefault);
            factory.verify(() -> IAMServiceFactory.register("keycloak", provider));
        }
    }
}
