/**
 * 파일 역할: 태스크 SKIP 요청 바디 DTO
 *
 * 기능:
 * - POST /work-item/{taskId}/skip 요청 모델
 * - endpoint/groupName/scope: SKIP 처리자. worklist·Lane RoleMapping에 무조건 덮어씀
 * - 필수 조합은 workitem.assignType({@code org.uengine.kernel.Role} ASSIGNTYPE) 기준
 *   - 0 ASSIGNTYPE_USER: endpoint
 *   - 3 ASSIGNTYPE_GROUP: groupName + endpoint
 *   - 4 ASSIGNTYPE_ROLE: scope + endpoint
 *   - 5 ASSIGNTYPE_GROUP_ROLE: groupName + scope + endpoint
 * - reason: SKIP 사유(선택)
 */
package org.uengine.five.dto;

public class TaskSkipCommand {

    String reason;
    /** SKIP 처리자 사번/엔드포인트 (필수) */
    String endpoint;
    /** 처리자 기관/그룹 (assignType 3,5 필수) */
    String groupName;
    /** 처리자 권한/스코프 (assignType 4,5 필수) */
    String scope;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}

