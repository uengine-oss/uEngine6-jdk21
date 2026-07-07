package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 권한 목록 조회 응답.
 */
public class RoleSearchResponse {

    private List<FncgRoleInfo> fncgCoreAtrtList = new ArrayList<>();

    public List<FncgRoleInfo> getFncgCoreAtrtList() {
        return fncgCoreAtrtList;
    }

    public void setFncgCoreAtrtList(List<FncgRoleInfo> fncgCoreAtrtList) {
        this.fncgCoreAtrtList = fncgCoreAtrtList != null ? fncgCoreAtrtList : new ArrayList<>();
    }
}
