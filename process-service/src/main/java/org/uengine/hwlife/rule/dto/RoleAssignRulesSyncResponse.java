package org.uengine.hwlife.rule.dto;

import java.util.List;

public class RoleAssignRulesSyncResponse {

    private String cpabNm;
    private List<RoleAssignRulesSyncResponseItem> cpabList;

    public String getCpabNm() {
        return cpabNm;
    }

    public void setCpabNm(String cpabNm) {
        this.cpabNm = cpabNm;
    }

    public List<RoleAssignRulesSyncResponseItem> getCpabList() {
        return cpabList;
    }

    public void setCpabList(List<RoleAssignRulesSyncResponseItem> cpabList) {
        this.cpabList = cpabList;
    }
}
