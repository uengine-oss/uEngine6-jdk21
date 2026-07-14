package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 반송 응답 — POST /instance/return.
 */
public class TaskReturnResponse {

    private String prcsRsltCntn;

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
