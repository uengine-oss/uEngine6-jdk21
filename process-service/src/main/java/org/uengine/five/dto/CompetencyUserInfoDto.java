package org.uengine.five.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 그룹·권한(역할) 조회 맥락에서 역량 기준정보가 함께 필요한 사용자 1건.
 *
 * <p>담당자 후보 목록, IAM 연동 등에서 공통으로 사용합니다.
 * 외부(ESB 등) 응답 스키마에 맞춰 {@link #competencyCriteria} 키를 확장하면 됩니다.</p>
 */
public class CompetencyUserInfoDto {

    private String groupCode;
    /** 그룹+권한 조회 시에만 설정될 수 있음 */
    private String permissionCode;
    private String userId;
    private String employeeNo;
    private String userName;
    /** 역량 기준정보 (외부 필드 매핑) */
    private Map<String, Object> competencyCriteria = new LinkedHashMap<>();

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Map<String, Object> getCompetencyCriteria() {
        return competencyCriteria != null ? Collections.unmodifiableMap(competencyCriteria) : Map.of();
    }

    public void setCompetencyCriteria(Map<String, Object> competencyCriteria) {
        this.competencyCriteria = competencyCriteria != null ? new LinkedHashMap<>(competencyCriteria) : new LinkedHashMap<>();
    }
}
