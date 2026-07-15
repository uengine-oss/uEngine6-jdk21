package org.uengine.five.messaging.polling.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

public class EventInboxRequest {
    @JsonAlias({ "eventname", "eventName", "eventNm", "evntNm" })
    private String eventName;

    @JsonAlias({ "corrkey", "corrKey", "loanPcesMgmtNo" })
    private String corrKey;

    private JsonNode payload;

    @JsonAlias({ "prcsRsltCodeNm" })
    private String prcrRsltCodeNm;

    private String prcsRsltCntn;

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getCorrKey() {
        return corrKey;
    }

    public void setCorrKey(String corrKey) {
        this.corrKey = corrKey;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getPrcrRsltCodeNm() {
        return prcrRsltCodeNm;
    }

    public void setPrcrRsltCodeNm(String prcrRsltCodeNm) {
        this.prcrRsltCodeNm = prcrRsltCodeNm;
    }

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
