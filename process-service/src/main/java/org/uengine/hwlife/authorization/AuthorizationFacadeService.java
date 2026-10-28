package org.uengine.hwlife.authorization;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.dto.deploy.ReleaseApplyRequest;
import org.uengine.five.dto.deploy.ReleaseApplyResponse;
import org.uengine.five.dto.deploy.ReleaseListResponse;
import org.uengine.five.dto.deploy.ReleaseRequestCreateRequest;
import org.uengine.five.dto.deploy.ReleaseRequestCreateResult;
import org.uengine.five.service.DefinitionDeployService;

/**
 * 인증·권한 검사 후 업무 API에 위임하는 공통 진입점.
 *
 * <pre>
 * UI → AuthorizationFacadeService
 *    → AuthorityFacade / IamAuthorityFacadeService
 *    → IAMServiceFactory (iam.provider: external | keycloak …)
 *    → DefinitionDeployLocalImpl (배포 시)
 * </pre>
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class AuthorizationFacadeService {

    /**
     * 배포 요청/적용 필수 권한 코드.
     * 사용자 보유 권한과 <b>하나라도</b> 겹치면 통과.
     */
    public static final List<String> DEPLOY_REQUIRED_AUTHORITIES = List.of("FN999");

    private final AuthorityFacade authorityFacade;
    private final DefinitionDeployService definitionDeployService;

    public AuthorizationFacadeService(
            AuthorityFacade authorityFacade,
            DefinitionDeployService definitionDeployService) {
        this.authorityFacade = authorityFacade;
        this.definitionDeployService = definitionDeployService;
    }

    @RequestMapping(value = "/deploy/request", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ReleaseListResponse listDeployRequests() throws Exception {
        authorityFacade.requireAnyAuthority(DEPLOY_REQUIRED_AUTHORITIES);
        return definitionDeployService.listDeployRequests();
    }

    @RequestMapping(value = "/deploy/request", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public ReleaseRequestCreateResult createDeployRequest(@RequestBody ReleaseRequestCreateRequest request)
            throws Exception {
        authorityFacade.requireAnyAuthority(DEPLOY_REQUIRED_AUTHORITIES);
        return definitionDeployService.createDeployRequest(request);
    }

    @RequestMapping(value = "/deploy/apply", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ReleaseListResponse listDeployApplies() throws Exception {
        authorityFacade.requireAnyAuthority(DEPLOY_REQUIRED_AUTHORITIES);
        return definitionDeployService.listDeployApplies();
    }

    @RequestMapping(value = "/deploy/apply", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public ReleaseApplyResponse applyDeploy(@RequestBody ReleaseApplyRequest request) throws Exception {
        authorityFacade.requireAnyAuthority(DEPLOY_REQUIRED_AUTHORITIES);
        return definitionDeployService.applyDeploy(request);
    }
}
