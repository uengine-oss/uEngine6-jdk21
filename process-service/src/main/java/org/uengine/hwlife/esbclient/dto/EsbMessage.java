package org.uengine.hwlife.esbclient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ESB 메시지 목록({@code msgeList}) 1건.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EsbMessage {

    private String msgeCode;
    private String msgeCntn;
    private String msgeOtptDvsnCode;

    public String getMsgeCode() { return msgeCode; }
    public void setMsgeCode(String msgeCode) { this.msgeCode = msgeCode; }
    public String getMsgeCntn() { return msgeCntn; }
    public void setMsgeCntn(String msgeCntn) { this.msgeCntn = msgeCntn; }
    public String getMsgeOtptDvsnCode() { return msgeOtptDvsnCode; }
    public void setMsgeOtptDvsnCode(String msgeOtptDvsnCode) { this.msgeOtptDvsnCode = msgeOtptDvsnCode; }
}
