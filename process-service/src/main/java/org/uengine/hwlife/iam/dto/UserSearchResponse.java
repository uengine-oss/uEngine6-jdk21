package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 조회 API 응답용 사용자 정보 DTO.
 */
public class UserSearchResponse {

    /** 사번 */
    private String hndrEmnb;
    /** 소속 기관(그룹) 코드 목록 */
    private List<FncgOrgInfo> fncgWndwCodeList = new ArrayList<>();
    /** 보유 권한 코드 목록 */
    private List<FncgRoleInfo> fncgCoreAtrtList = new ArrayList<>();

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public List<FncgOrgInfo> getFncgWndwCodeList() {
        return fncgWndwCodeList;
    }

    public void setFncgWndwCodeList(List<FncgOrgInfo> fncgWndwCodeList) {
        this.fncgWndwCodeList = fncgWndwCodeList != null ? fncgWndwCodeList : new ArrayList<>();
    }

    public List<FncgRoleInfo> getFncgCoreAtrtList() {
        return fncgCoreAtrtList;
    }

    public void setFncgCoreAtrtList(List<FncgRoleInfo> fncgCoreAtrtList) {
        this.fncgCoreAtrtList = fncgCoreAtrtList != null ? fncgCoreAtrtList : new ArrayList<>();
    }
}
