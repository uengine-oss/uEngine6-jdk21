package org.uengine.hwlife.iam;

import org.springframework.web.bind.annotation.RestController;
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
        response.setFncgOrgnCodeList(externalIamService.getGroups());
        return response;
    }

    @Override
    public RoleSearchResponse searchRoles() throws Exception {
        RoleSearchResponse response = new RoleSearchResponse();
        response.setFncgCoreAtrtList(externalIamService.getRoles());
        return response;
    }

    @Override
    public UserSearchResponse searchUser(UserSearchRequest request) throws Exception {
        UserSearchResponse response = new UserSearchResponse();
        if (request == null || request.getHndrEmnb() == null || request.getHndrEmnb().isBlank()) {
            return response;
        }

        
        return response;
    }

    @Override
    public ChangedIamSyncResponse syncChangedIam(ChangedIamSyncRequest request) throws Exception {
        return new ChangedIamSyncResponse();
    }
}
