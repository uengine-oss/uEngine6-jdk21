package org.uengine.hwlife.iam.dto;

/**
 * 권한(역할) 정보를 담는 공통 DTO.
 *
 * <p>그룹과 독립적으로 조회·선택하는 권한 기준정보(코드·이름)입니다.
 * 외부(ESB 등) 응답 필드명이 다르면 매핑만 맞추면 되고, 필드가 늘면 이 클래스를 확장하거나
 * 상세 전용 DTO를 분리하면 됩니다.</p>
 */
public class RoleInfoDto {

    /** 권한 코드 */
    private String roleCode;
    /** 권한명 */
    private String roleName;

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
