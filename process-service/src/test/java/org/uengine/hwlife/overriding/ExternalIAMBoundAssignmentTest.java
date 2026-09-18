package org.uengine.hwlife.overriding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.uengine.five.overriding.IAMRoleResolutionContext;
import org.uengine.five.service.IAMService;
import org.uengine.hwlife.iam.ExternalIAMService;
import org.uengine.hwlife.iam.dto.FncgOrgInfo;
import org.uengine.hwlife.iam.dto.FncgRoleInfo;
import org.uengine.hwlife.iam.dto.OrgSearchResponse;
import org.uengine.hwlife.iam.dto.RoleSearchResponse;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleMapping;

class ExternalIAMBoundAssignmentTest {
    // @Test
    // void validFieldsAreIndependentOfMemberIntersectionAndInvalidCounterpart() throws Exception {
    //     IAMService provider = mock(IAMService.class);
    //     when(provider.isValidGroup("ORG02")).thenReturn(true);
    //     when(provider.isValidRole("ROLE02")).thenReturn(true);
    //     BoundRoleResolutionContext context = context(provider);
    //     for (String[] values : new String[][] {{"ORG02", null}, {null, "ROLE02"},
    //             {"ORG02", "ROLE02"}, {"ORG02", "UNKNOWN"}, {"UNKNOWN", "ROLE02"}}) {
    //         ProcessInstance instance = mock(ProcessInstance.class);
    //         when(instance.get("", "orgCode")).thenReturn(values[0]);
    //         when(instance.get("", "roleCode")).thenReturn(values[1]);
    //         RoleMapping inherited = current();
    //         RoleMapping next = context.resolveRoleMapping(null, instance, "Task_2", inherited, Map.of());
    //         assertEquals("ORG02".equals(values[0]) ? "ORG02" : "ORG01", next.getGroupName());
    //         assertEquals("ROLE02".equals(values[1]) ? "ROLE02" : "ROLE01", next.getScope());
    //         assertNull(next.getEndpoint());
    //         assertEquals("claimed-user", inherited.getEndpoint());
    //         ProcessInstance empty = mock(ProcessInstance.class);
    //         assertSame(next, context.resolveRoleMapping(null, empty, "Task_3", next, Map.of()));
    //     }
    //     verify(provider, never()).isValidGroupRole(anyString(), anyString());
    //     verify(provider, never()).getUsersByGroup(anyString());
    // }

    // @Test
    // void combinesPartialChangesUsingExternalCatalogsAndPreservesExistingClaim() throws Exception {
    //     ExternalIAMService provider = mock(ExternalIAMService.class, CALLS_REAL_METHODS);
    //     OrgSearchResponse orgs = new OrgSearchResponse();
    //     orgs.setBpmOrgnList(List.of(org("ORG01"), org("ORG02")));
    //     RoleSearchResponse roles = new RoleSearchResponse();
    //     roles.setBpmAtrtList(List.of(role("ROLE01"), role("ROLE02")));
    //     doReturn(orgs).when(provider).getGroups();
    //     doReturn(roles).when(provider).getRoles();
    //     BoundRoleResolutionContext context = context(provider);
    //     for (String[] values : new String[][] {{"ORG02", null}, {null, "ROLE02"}, {"ORG02", "ROLE02"}}) {
    //         ProcessInstance instance = mock(ProcessInstance.class);
    //         when(instance.get("", "orgCode")).thenReturn(values[0]);
    //         when(instance.get("", "roleCode")).thenReturn(values[1]);
    //         RoleMapping current = current();
    //         RoleMapping next = context.resolveRoleMapping(null, instance, "Task_2", current, Map.of());
    //         assertEquals(values[0] == null ? "ORG01" : values[0], next.getGroupName());
    //         assertEquals(values[1] == null ? "ROLE01" : values[1], next.getScope());
    //         assertNull(next.getEndpoint());
    //         assertEquals("claimed-user", current.getEndpoint());
    //         assertEquals("ORG01", current.getGroupName());
    //         assertEquals("ROLE01", current.getScope());
    //     }
    // }

    // @Test
    // void unavailableExternalCatalogKeepsClaimInsteadOfAcceptingUncheckedVariable() throws Exception {
    //     ExternalIAMService provider = mock(ExternalIAMService.class, CALLS_REAL_METHODS);
    //     doThrow(new IllegalStateException("external catalog unavailable")).when(provider).getGroups();
    //     ProcessInstance instance = mock(ProcessInstance.class);
    //     when(instance.get("", "orgCode")).thenReturn("ORG02");
    //     RoleMapping current = current();
    //     assertSame(current, context(provider).resolveRoleMapping(null, instance, "Task_2", current, Map.of()));
    //     verify(provider).getGroups();
    // }

    // // private static BoundRoleResolutionContext context(IAMService provider) {
    //     // BoundRoleResolutionContext context = new BoundRoleResolutionContext() {
    //     //     @Override protected IAMService getIamService() { return provider; }
    //     // };
    //     // IAMRoleResolutionContext base = new IAMRoleResolutionContext();
    //     // base.setGroupName("DEFAULT_ORG");
    //     // base.setScope("DEFAULT_ROLE");
    //     // context.setBase(base);
    //     // context.setBindings(new LinkedHashMap<>(Map.of("groupName", "orgCode", "scope", "roleCode")));
    //     // return context;
    // // }

    // private static RoleMapping current() {
    //     RoleMapping current = RoleMapping.create();
    //     current.setGroupName("ORG01");
    //     current.setScope("ROLE01");
    //     current.setEndpoint("claimed-user");
    //     return current;
    // }

    // private static FncgOrgInfo org(String code) {
    //     FncgOrgInfo org = new FncgOrgInfo();
    //     org.setFncgWndwOrgnCode(code);
    //     return org;
    // }

    // private static FncgRoleInfo role(String code) {
    //     FncgRoleInfo role = new FncgRoleInfo();
    //     role.setFncgCoreAtrtId(code);
    //     return role;
    // }
}
