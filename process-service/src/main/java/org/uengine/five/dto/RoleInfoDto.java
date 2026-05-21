package org.uengine.five.dto;

/**
 * 역할(권한) 정보를 담는 공통 DTO.
 *
 * <p>그룹 단위 역할 목록 등에서 {@code groupCode}와 역할 코드·이름을 함께 쓸 수 있습니다.
 * 외부(ESB 등) 응답 필드명이 다르면 매핑만 맞추면 되고, 필드가 늘면 이 클래스를 확장하거나
 * 상세 전용 DTO를 분리하면 됩니다.</p>
 */
public class RoleInfoDto {

    /** 소속 그룹 코드(선택·컨텍스트에 따라 생략 가능) */
    private String groupCode;
    /** 역할 코드 */
    private String roleCode;
    /** 역할명 */
    private String roleName;

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
