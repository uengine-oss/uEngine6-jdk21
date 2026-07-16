package org.uengine.hwlife.rule.dto;

public class RoleAssignSyncResponseItem {

    private String hndrEmnb;
    private String cpabLvdfLvelNm;
    private Integer cpabWghdCnt;
    private String useYn;

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getCpabLvdfLvelNm() {
        return cpabLvdfLvelNm;
    }

    public void setCpabLvdfLvelNm(String cpabLvdfLvelNm) {
        this.cpabLvdfLvelNm = cpabLvdfLvelNm;
    }

    public Integer getCpabWghdCnt() {
        return cpabWghdCnt;
    }

    public void setCpabWghdCnt(Integer cpabWghdCnt) {
        this.cpabWghdCnt = cpabWghdCnt;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }
}
