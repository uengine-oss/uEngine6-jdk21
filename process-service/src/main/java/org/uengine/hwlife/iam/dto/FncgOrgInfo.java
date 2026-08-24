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
    private String orgnCode;
    private String orgnNm;
    private String orgnAbrvNm;
    private String hgrnOrgnCode;
    private String fncgWndwOrgnCode;
   

    public String getOrgnCode() {
        return orgnCode;
    }

    public void setOrgnCode(String orgnCode) {
        this.orgnCode = orgnCode;
    }

    public String getOrgnNm() {
        return orgnNm;
    }

    public void setOrgnNm(String orgnNm) {
        this.orgnNm = orgnNm;
    }

    public String getOrgnAbrvNm() {
        return orgnAbrvNm;
    }

    public void setOrgnAbrvNm(String orgnAbrvNm) {
        this.orgnAbrvNm = orgnAbrvNm;
    }

    public String getHgrnOrgnCode() {
        return hgrnOrgnCode;
    }

    public void setHgrnOrgnCode(String hgrnOrgnCode) {
        this.hgrnOrgnCode = hgrnOrgnCode;
    }

    public String getFncgWndwOrgnCode() {
        return fncgWndwOrgnCode;
    }

    public void setFncgWndwOrgnCode(String fncgWndwOrgnCode) {
        this.fncgWndwOrgnCode = fncgWndwOrgnCode;
    }

}
