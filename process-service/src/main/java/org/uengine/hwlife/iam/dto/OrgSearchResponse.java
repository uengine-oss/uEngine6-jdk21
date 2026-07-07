package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 기관(그룹) 목록 조회 응답.
 */
public class OrgSearchResponse {

    private List<FncgOrgInfo> fncgOrgnCodeList = new ArrayList<>();

    public List<FncgOrgInfo> getFncgOrgnCodeList() {
        return fncgOrgnCodeList;
    }

    public void setFncgOrgnCodeList(List<FncgOrgInfo> fncgOrgnCodeList) {
        this.fncgOrgnCodeList = fncgOrgnCodeList != null ? fncgOrgnCodeList : new ArrayList<>();
    }
}
