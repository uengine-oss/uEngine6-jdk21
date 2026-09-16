package org.uengine.hwlife.iam;

import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.service.IAMServiceFactory;
import org.uengine.five.service.KeycloakIAMService;
import org.uengine.hwlife.iam.dto.FncgOrgInfo;
import org.uengine.hwlife.iam.dto.FncgRoleInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.uengine.hwlife.iam.dto.ChangedIamSyncRequest;
import org.uengine.hwlife.iam.dto.ChangedIamSyncResponse;
import org.uengine.hwlife.iam.dto.RoleSearchResponse;
import org.uengine.hwlife.iam.dto.UserSearchRequest;
import org.uengine.hwlife.iam.dto.UserSearchResponse;
import org.uengine.hwlife.iam.dto.OrgSearchResponse;

/**
 * {@link IAMIntegrationService} REST 구현. {@link ExternalIAMService}를 통해 외부 IAM을 조회합니다.
 */
@RestController
public class IAMIntegrationServiceImpl implements IAMIntegrationService {

    private final ExternalIAMService externalIamService = ExternalIAMService.getDefault();

    @Override
    public OrgSearchResponse searchOrgs() throws Exception {
        OrgSearchResponse response = new OrgSearchResponse();
        if (getIamService() instanceof KeycloakIAMService keycloak) {
            List<FncgOrgInfo> groups = new ArrayList<>();
            addKeycloakGroups(keycloak.getGroupCandidates(), groups);
            response.setBpmOrgnList(groups);
            return response;
        }
        response.setBpmOrgnList(getExternalIamService().getGroups().getBpmOrgnList());
        return response;
    }

    @Override
    public RoleSearchResponse searchRoles() throws Exception {
        RoleSearchResponse response = new RoleSearchResponse();
        if (getIamService() instanceof KeycloakIAMService keycloak) {
            List<FncgRoleInfo> roles = new ArrayList<>();
            for (Map<String, Object> candidate : keycloak.getRoleCandidates()) {
                FncgRoleInfo role = new FncgRoleInfo();
                role.setFncgCoreAtrtId((String) candidate.get("name"));
                role.setFncgCoreAtrtNm((String) candidate.get("name"));
                roles.add(role);
            }
            response.setBpmAtrtList(roles);
            return response;
        }
        response.setBpmAtrtList(getExternalIamService().getRoles().getBpmAtrtList());
        return response;
    }

    @SuppressWarnings("unchecked")
    private static void addKeycloakGroups(List<Map<String, Object>> candidates, List<FncgOrgInfo> groups) {
        for (Map<String, Object> candidate : candidates) {
            FncgOrgInfo group = new FncgOrgInfo();
            group.setFncgWndwOrgnCode((String) candidate.get("name"));
            group.setFncgWndwOrgnNm((String) candidate.get("name"));
            groups.add(group);
            if (candidate.get("subGroups") instanceof List<?> children) {
                addKeycloakGroups((List<Map<String, Object>>) (List<?>) children, groups);
            }
        }
    }

    protected org.uengine.five.service.IAMService getIamService() {
        return IAMServiceFactory.getDefault();
    }

    protected ExternalIAMService getExternalIamService() {
        return externalIamService;
    }

    @Override
    public UserSearchResponse searchUser(UserSearchRequest request) throws Exception {
        if (request == null || request.getHndrEmnb() == null || request.getHndrEmnb().isBlank()) {
            return null;
        }
        UserSearchResponse response = externalIamService.getUser(request.getHndrEmnb());
        // 유효하지 않은 사번(필드가 비어 있는 응답)도 null 로 통일
        if (response == null || response.getHndrEmnb() == null || response.getHndrEmnb().isBlank()) {
            return null;
        }
        return response;
    }

    @Override
    public ChangedIamSyncResponse syncChangedIam(ChangedIamSyncRequest request) throws Exception {
        return new ChangedIamSyncResponse();
    }
}
