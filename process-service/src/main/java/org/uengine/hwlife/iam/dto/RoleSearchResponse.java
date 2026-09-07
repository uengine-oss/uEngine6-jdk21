package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 권한 목록 조회 응답.
 */
public class RoleSearchResponse {

    private List<FncgRoleInfo> bpmAtrtList = new ArrayList<>();

    public List<FncgRoleInfo> getBpmAtrtList() {
        return bpmAtrtList;
    }

    public void setBpmAtrtList(List<FncgRoleInfo> bpmAtrtList) {
        this.bpmAtrtList = bpmAtrtList != null ? bpmAtrtList : new ArrayList<>();
    }
}
