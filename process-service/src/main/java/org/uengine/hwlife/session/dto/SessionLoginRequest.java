package org.uengine.hwlife.session.dto;

public class SessionLoginRequest {

    private String emnb;
    private String lognPswd;
    private Integer idleTimeoutMinutes;

    public String getEmnb() {
        return emnb;
    }

    public void setEmnb(String emnb) {
        this.emnb = emnb;
    }

    public String getLognPswd() {
        return lognPswd;
    }

    public void setLognPswd(String lognPswd) {
        this.lognPswd = lognPswd;
    }

    public Integer getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(Integer idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }
}
