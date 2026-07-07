package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 요청.
 *
 * <p>{@code changeType}: {@code ORG_MERGE}(기관 통폐합), {@code HR_CHANGE}(인사변동)</p>
 */
public class ChangedIamSyncRequest {

    /** 변경 유형: ORG_MERGE | HR_CHANGE */
    private String changeType;

    // --- 기관 통폐합 (ORG_MERGE) ---
    /** 폐지·통합 대상 기관 코드 목록 */
    private List<String> abolishedOrgCodes = new ArrayList<>();
    /** 통합 후 기관 코드 */
    private String targetOrgCode;
    /** 통합 후 기관 명 */
    private String targetOrgName;

    // --- 인사변동 (HR_CHANGE) ---
    /** 대상 사번 */
    private String emnb;
    /** 변경 전 기관 코드 (선택, 대상 업무 필터) */
    private String beforeOrgCode;
    /** 변경 후 기관 코드 */
    private String afterOrgCode;
    /** 변경 후 권한 코드 목록 (선택) */
    private List<String> afterRoleIds = new ArrayList<>();

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public List<String> getAbolishedOrgCodes() {
        return abolishedOrgCodes;
    }

    public void setAbolishedOrgCodes(List<String> abolishedOrgCodes) {
        this.abolishedOrgCodes = abolishedOrgCodes != null ? abolishedOrgCodes : new ArrayList<>();
    }

    public String getTargetOrgCode() {
        return targetOrgCode;
    }

    public void setTargetOrgCode(String targetOrgCode) {
        this.targetOrgCode = targetOrgCode;
    }

    public String getTargetOrgName() {
        return targetOrgName;
    }

    public void setTargetOrgName(String targetOrgName) {
        this.targetOrgName = targetOrgName;
    }

    public String getEmnb() {
        return emnb;
    }

    public void setEmnb(String emnb) {
        this.emnb = emnb;
    }

    public String getBeforeOrgCode() {
        return beforeOrgCode;
    }

    public void setBeforeOrgCode(String beforeOrgCode) {
        this.beforeOrgCode = beforeOrgCode;
    }

    public String getAfterOrgCode() {
        return afterOrgCode;
    }

    public void setAfterOrgCode(String afterOrgCode) {
        this.afterOrgCode = afterOrgCode;
    }

    public List<String> getAfterRoleIds() {
        return afterRoleIds;
    }

    public void setAfterRoleIds(List<String> afterRoleIds) {
        this.afterRoleIds = afterRoleIds != null ? afterRoleIds : new ArrayList<>();
    }
}
