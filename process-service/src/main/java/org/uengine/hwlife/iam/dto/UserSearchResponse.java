package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 조회 API 응답용 사용자 정보 DTO.
 */
public class UserSearchResponse {

    private String hndrEmnb; // 사원번호
    private String hndrNm; // 사원명

    private List<FncgOrgInfo> bpmOrgnList = new ArrayList<>();    /** 보유 기관 코드 목록 */
    private List<FncgRoleInfo> fncgCoreAtrtList = new ArrayList<>();     /** 보유 권한 코드 목록 */

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getHndrNm() {
        return hndrNm;
    }

    public void setHndrNm(String hndrNm) {
        this.hndrNm = hndrNm;
    }

    public List<FncgOrgInfo> getBpmOrgnList() {
        return bpmOrgnList;
    }

    public void setBpmOrgnList(List<FncgOrgInfo> bpmOrgnList) {
        this.bpmOrgnList = bpmOrgnList != null ? bpmOrgnList : new ArrayList<>();
    }

    public List<FncgRoleInfo> getFncgCoreAtrtList() {
        return fncgCoreAtrtList;
    }

    public void setFncgCoreAtrtList(List<FncgRoleInfo> fncgCoreAtrtList) {
        this.fncgCoreAtrtList = fncgCoreAtrtList != null ? fncgCoreAtrtList : new ArrayList<>();
    }
}
