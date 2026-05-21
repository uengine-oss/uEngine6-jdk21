package org.uengine.five.dto;

/**
 * 그룹 정보(코드·이름 등)를 담는 공통 DTO.
 *
 * <p>조직도·IAM·워크리스트 등 그룹 단위 조회 API에서 동일 스키마로 재사용합니다.
 * 외부(ESB 등) 응답 필드명이 다르면 매핑만 맞추면 되고, 필드가 늘면 이 클래스를 확장하거나
 * 상세 전용 DTO를 분리하면 됩니다.</p>
 */
public class GroupInfoDto {

    /** 그룹 코드 */
    private String groupCode;
    /** 그룹명 */
    private String groupName;

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
