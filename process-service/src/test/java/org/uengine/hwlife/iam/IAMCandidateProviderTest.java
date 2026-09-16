package org.uengine.hwlife.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.uengine.five.service.IAMService;
import org.uengine.five.service.KeycloakIAMService;
import org.uengine.hwlife.iam.dto.FncgOrgInfo;
import org.uengine.hwlife.iam.dto.FncgRoleInfo;
import org.uengine.hwlife.iam.dto.OrgSearchResponse;
import org.uengine.hwlife.iam.dto.RoleSearchResponse;

class IAMCandidateProviderTest {

    @Test
    void keycloakModeMapsNestedGroupsAndRoles() throws Exception {
        KeycloakIAMService keycloak = mock(KeycloakIAMService.class);
        when(keycloak.getGroupCandidates()).thenReturn(List.of(
                Map.of("name", "parent", "subGroups", List.of(Map.of("name", "child")))));
        when(keycloak.getRoleCandidates()).thenReturn(List.of(Map.of("name", "approver")));

        IAMIntegrationServiceImpl service = serviceUsing(keycloak);

        assertEquals(List.of("parent", "child"), service.searchOrgs().getBpmOrgnList().stream()
                .map(FncgOrgInfo::getFncgWndwOrgnCode).toList());
        assertEquals(List.of("approver"), service.searchRoles().getBpmAtrtList().stream()
                .map(FncgRoleInfo::getFncgCoreAtrtId).toList());
    }

    @Test
    void externalModeKeepsExistingCandidateSource() throws Exception {
        IAMService externalProvider = mock(IAMService.class);
        OrgSearchResponse orgs = new OrgSearchResponse();
        FncgOrgInfo org = new FncgOrgInfo();
        org.setFncgWndwOrgnCode("ORG01");
        orgs.setBpmOrgnList(List.of(org));
        RoleSearchResponse roles = new RoleSearchResponse();
        FncgRoleInfo role = new FncgRoleInfo();
        role.setFncgCoreAtrtId("ROLE01");
        roles.setBpmAtrtList(List.of(role));
        ExternalIAMService externalIam = mock(ExternalIAMService.class);
        when(externalIam.getGroups()).thenReturn(orgs);
        when(externalIam.getRoles()).thenReturn(roles);

        IAMIntegrationServiceImpl service = serviceUsing(externalProvider, externalIam);

        assertEquals("ORG01", service.searchOrgs().getBpmOrgnList().get(0).getFncgWndwOrgnCode());
        assertEquals("ROLE01", service.searchRoles().getBpmAtrtList().get(0).getFncgCoreAtrtId());
    }

    @Test
    void keycloakLookupFailureIsPropagated() throws Exception {
        KeycloakIAMService keycloak = mock(KeycloakIAMService.class);
        when(keycloak.getGroupCandidates()).thenThrow(new IOException("unavailable"));

        assertThrows(IOException.class, () -> serviceUsing(keycloak).searchOrgs());
    }

    private static IAMIntegrationServiceImpl serviceUsing(IAMService iamService) {
        return serviceUsing(iamService, ExternalIAMService.getDefault());
    }

    private static IAMIntegrationServiceImpl serviceUsing(
            IAMService iamService, ExternalIAMService externalIamService) {
        return new IAMIntegrationServiceImpl() {
            @Override
            protected IAMService getIamService() {
                return iamService;
            }

            @Override
            protected ExternalIAMService getExternalIamService() {
                return externalIamService;
            }
        };
    }
}
