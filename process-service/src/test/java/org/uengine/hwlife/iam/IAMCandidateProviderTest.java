package org.uengine.hwlife.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.uengine.five.service.IAMServiceFactory;
import org.uengine.hwlife.iam.dto.FncgOrgInfo;
import org.uengine.hwlife.iam.dto.FncgRoleInfo;
import org.uengine.hwlife.iam.dto.OrgSearchResponse;
import org.uengine.hwlife.iam.dto.RoleSearchResponse;

/** External REST contracts must not dispatch through the shared provider factory. */
class IAMCandidateProviderTest {
    @Test
    void externalModeKeepsExistingCandidateSource() throws Exception {
        OrgSearchResponse orgs = new OrgSearchResponse();
        FncgOrgInfo org = new FncgOrgInfo();
        org.setFncgWndwOrgnCode("ORG01");
        orgs.setBpmOrgnList(List.of(org));
        RoleSearchResponse roles = new RoleSearchResponse();
        FncgRoleInfo role = new FncgRoleInfo();
        role.setFncgCoreAtrtId("ROLE01");
        roles.setBpmAtrtList(List.of(role));
        ExternalIAMService external = mock(ExternalIAMService.class);
        when(external.getGroups()).thenReturn(orgs);
        when(external.getRoles()).thenReturn(roles);
        try (MockedStatic<IAMServiceFactory> factory = mockStatic(IAMServiceFactory.class)) {
            IAMIntegrationServiceImpl service = serviceUsing(external);
            assertEquals("ORG01", service.searchOrgs().getBpmOrgnList().get(0).getFncgWndwOrgnCode());
            assertEquals("ROLE01", service.searchRoles().getBpmAtrtList().get(0).getFncgCoreAtrtId());
            factory.verifyNoInteractions();
        }
    }

    @Test
    void externalLookupFailureIsPropagatedWithoutProviderFallback() {
        ExternalIAMService external = mock(ExternalIAMService.class);
        when(external.getGroups()).thenThrow(new IllegalStateException("unavailable"));
        try (MockedStatic<IAMServiceFactory> factory = mockStatic(IAMServiceFactory.class)) {
            assertThrows(IllegalStateException.class, () -> serviceUsing(external).searchOrgs());
            factory.verifyNoInteractions();
        }
    }

    private static IAMIntegrationServiceImpl serviceUsing(ExternalIAMService external) {
        try (MockedStatic<ExternalIAMService> singleton = mockStatic(ExternalIAMService.class)) {
            singleton.when(ExternalIAMService::getDefault).thenReturn(external);
            return new IAMIntegrationServiceImpl();
        }
    }
}
