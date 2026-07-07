package org.uengine.hwlife.iam;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.hwlife.iam.dto.ChangedIamSyncRequest;
import org.uengine.hwlife.iam.dto.ChangedIamSyncResponse;
import org.uengine.hwlife.iam.dto.RoleSearchResponse;
import org.uengine.hwlife.iam.dto.UserSearchRequest;
import org.uengine.hwlife.iam.dto.UserSearchResponse;
import org.uengine.hwlife.iam.dto.OrgSearchResponse;

/**
 * 외부 IAM 연동 REST API — 조회 및 변경 수신·반영.
 *
 * <p>구현: {@link IAMIntegrationServiceImpl}. {@link ExternalIAMService}를 통해 외부 IAM을 조회합니다.</p>
 *
 * <pre>
 *   POST /iam/orgs       기관 목록 조회
 *   POST /iam/roles      권한 목록 조회
 *   POST /iam/assignees  body: { "fncgWndwCode": "...", "fncgCoreAtrtId": "..." }
 *   POST /iam/user       body: { "emnb": "..." }
 *   POST /iam/iam-sync   body: { "changeType": "ORG_MERGE|HR_CHANGE", ... }
 * </pre>
 */
@RequestMapping("/iam")
public interface IAMIntegrationService {

    // 기관 목록 조회
    @RequestMapping(value = "/orgs", method = RequestMethod.POST)
    OrgSearchResponse searchOrgs() throws Exception;

    // 권한 목록 조회
    @RequestMapping(value = "/roles", method = RequestMethod.POST)
    RoleSearchResponse searchRoles() throws Exception;

    // 사용자 단건 조회
    @RequestMapping(value = "/user", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    UserSearchResponse searchUser(@RequestBody UserSearchRequest request) throws Exception;

    // IAM 변경(기관 통폐합·인사변동) — 관련 인스턴스·업무 DB 반영
    @RequestMapping(value = "/iam-sync", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
    ChangedIamSyncResponse syncChangedIam(@RequestBody ChangedIamSyncRequest request) throws Exception;
}
