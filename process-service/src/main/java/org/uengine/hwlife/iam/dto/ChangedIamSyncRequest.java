package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 요청.
 *
 * <p>{@code changeType}: {@code ORG_MERGE}(기관 통폐합), {@code HR_CHANGE}(인사변동)</p>
 */
public class ChangedIamSyncRequest {

    private List<ChangedIamSyncRequestItem> chngList = new ArrayList<>();

    public List<ChangedIamSyncRequestItem> getChngList() {
        return chngList;
    }

    public void setChngList(List<ChangedIamSyncRequestItem> chngList) {
        this.chngList = chngList;
    }

}
