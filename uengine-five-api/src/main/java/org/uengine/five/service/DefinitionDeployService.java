package org.uengine.five.service;

import org.uengine.five.dto.deploy.ReleaseApplyRequest;
import org.uengine.five.dto.deploy.ReleaseApplyResponse;
import org.uengine.five.dto.deploy.ReleaseListResponse;
import org.uengine.five.dto.deploy.ReleaseRequestCreateRequest;
import org.uengine.five.dto.deploy.ReleaseRequestCreateResult;

/**
 * 배포 요청/적용 비즈니스 API.
 * process-service 가 공유 {@code basePath} 에서 로컬 구현한다 (Feign/네트워크 없음).
 * UI 진입·권한 검사는 {@code AuthorizationFacadeService}(+ {@code AuthorityFacade}) 가 담당한다.
 */
public interface DefinitionDeployService {

    ReleaseListResponse listDeployRequests() throws Exception;

    ReleaseRequestCreateResult createDeployRequest(ReleaseRequestCreateRequest request) throws Exception;

    ReleaseListResponse listDeployApplies() throws Exception;

    ReleaseApplyResponse applyDeploy(ReleaseApplyRequest request) throws Exception;
}
