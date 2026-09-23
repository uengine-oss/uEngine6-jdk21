package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 결과.
 */
public class ChangedIamSyncResponse {

    private int sucsCont;
    private int failCont;
    private List<ChangedIamSyncResponseItem> failList = new ArrayList<>();

    public int getSucsCont() {
        return sucsCont;
    }

    public void setSucsCont(int sucsCont) {
        this.sucsCont = sucsCont;
    }

    public int getFailCont() {
        return failCont;
    }

    public void setFailCont(int failCont) {
        this.failCont = failCont;
    }

    public List<ChangedIamSyncResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<ChangedIamSyncResponseItem> failList) {
        this.failList = failList;
    }
}
