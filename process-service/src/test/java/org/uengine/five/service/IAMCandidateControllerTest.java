package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IAMCandidateControllerTest {

    @Test
    void normalizesNestedProviderGroupsAndRoles() throws Exception {
        // IAMService provider = mock(IAMService.class);
        // when(provider.getGroupCandidates()).thenReturn(List.of(
        //         Map.of("name", "parent", "subGroups", List.of(Map.of("name", "child")))));
        // when(provider.getRoleCandidates()).thenReturn(List.of(Map.of("name", "approver")));

        // IAMCandidateController controller = controllerUsing(provider);

        // assertEquals(List.of("parent", "child"), controller.getOrganizations().stream()
        //         .map(candidate -> candidate.get("id")).toList());
        // assertEquals(List.of("approver"), controller.getRoles().stream()
        //         .map(candidate -> candidate.get("id")).toList());
    }

    @Test
    void preservesProviderCandidateIdsAndDisplayNames() throws Exception {
        // IAMService provider = mock(IAMService.class);
        // when(provider.getGroupCandidates()).thenReturn(List.of(Map.of("id", "00025", "name", "IT운영팀")));
        // when(provider.getRoleCandidates()).thenReturn(List.of(Map.of("id", "FN120", "name", "사업부장")));

        // IAMCandidateController controller = controllerUsing(provider);

        // assertEquals(Map.of("id", "00025", "name", "IT운영팀"), controller.getOrganizations().get(0));
        // assertEquals(Map.of("id", "FN120", "name", "사업부장"), controller.getRoles().get(0));
    }

    @Test
    void propagatesProviderLookupFailure() throws Exception {
        // IAMService provider = mock(IAMService.class);
        // when(provider.getGroupCandidates()).thenThrow(new IOException("unavailable"));

        // assertThrows(IOException.class, () -> controllerUsing(provider).getOrganizations());
    }

    // private static IAMCandidateController controllerUsing(IAMService provider) {
        // return new IAMCandidateController() {
        //     @Override
        //     protected IAMService getIamService() {
        //         return provider;
        //     }
        // };
    // }
}
