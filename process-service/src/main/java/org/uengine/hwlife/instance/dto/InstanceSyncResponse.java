package org.uengine.hwlife.instance.dto;

/**
 * 업무(인스턴스·워크리스트) 상태 동기화 응답 — POST /instance/sync.
 */
public class InstanceSyncResponse {

    private String prcsYn;

    public String getPrcsYn() {
        return prcsYn;
    }

    public void setPrcsYn(String prcsYn) {
        this.prcsYn = prcsYn;
    }
}
