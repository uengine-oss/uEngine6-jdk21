package org.uengine.hwlife.iam.dto;

/**
 * 그룹 정보(코드·이름 등)를 담는 공통 DTO.
 *
 * <p>조직도·IAM·워크리스트 등 그룹 단위 조회 API에서 동일 스키마로 재사용합니다.
 * 외부(ESB 등) 응답 필드명이 다르면 매핑만 맞추면 되고, 필드가 늘면 이 클래스를 확장하거나
 * 상세 전용 DTO를 분리하면 됩니다.</p>
 */
public class FncgOrgInfo {

    /** 기관 코드 */
    private String fncgWndwOrgnCode;
    /** 기관 명 */
    private String fncgWndwOrgnNm;

    public String getFncgWndwOrgnCode() {
        return fncgWndwOrgnCode;
    }

    public void setFncgWndwOrgnCode(String fncgWndwOrgnCode) {
        this.fncgWndwOrgnCode = fncgWndwOrgnCode;
    }

    public String getFncgWndwOrgnNm() {
        return fncgWndwOrgnNm;
    }

    public void setFncgWndwOrgnNm(String fncgWndwOrgnNm) {
        this.fncgWndwOrgnNm = fncgWndwOrgnNm;
    }
}
