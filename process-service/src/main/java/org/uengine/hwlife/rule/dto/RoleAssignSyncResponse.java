package org.uengine.hwlife.rule.dto;

import java.util.List;

public class RoleAssignSyncResponse {

    private String cpabNm;
    private List<RoleAssignSyncResponseItem> cpabList;

    public String getCpabNm() {
        return cpabNm;
    }

    public void setCpabNm(String cpabNm) {
        this.cpabNm = cpabNm;
    }

    public List<RoleAssignSyncResponseItem> getCpabList() {
        return cpabList;
    }

    public void setCpabList(List<RoleAssignSyncResponseItem> cpabList) {
        this.cpabList = cpabList;
    }
}
