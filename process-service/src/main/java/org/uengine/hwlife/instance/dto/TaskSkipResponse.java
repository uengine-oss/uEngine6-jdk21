package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 SKIP 응답 — POST /instance/skip.
 */
public class TaskSkipResponse {

    private String prcsRsltCntn;

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
