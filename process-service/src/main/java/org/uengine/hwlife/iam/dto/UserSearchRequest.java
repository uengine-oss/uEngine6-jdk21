package org.uengine.hwlife.iam.dto;

/**
 * 사번으로 사용자(담당자)를 단건 조회할 때 사용하는 요청 DTO.
 */
public class UserSearchRequest {

    /** 조회 대상 사번 */
    private String hndrEmnb;

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }
}
