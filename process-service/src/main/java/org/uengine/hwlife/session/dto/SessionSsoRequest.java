package org.uengine.hwlife.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionSsoRequest {

    @JsonProperty("SM_SERVERSESSIONID")
    private String smServerSessionId;

    private Integer idleTimeoutMinutes;

    public String getSmServerSessionId() {
        return smServerSessionId;
    }

    public void setSmServerSessionId(String smServerSessionId) {
        this.smServerSessionId = smServerSessionId;
    }

    public Integer getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(Integer idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }
}
