package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 기관(그룹) 목록 조회 응답.
 */
public class OrgSearchResponse {

    private List<FncgOrgInfo> bpmOrgnList = new ArrayList<>();

    public List<FncgOrgInfo> getBpmOrgnList() {
        return bpmOrgnList;
    }

    public void setBpmOrgnList(List<FncgOrgInfo> bpmOrgnList) {
        this.bpmOrgnList = bpmOrgnList != null ? bpmOrgnList : new ArrayList<>();
    }
}
