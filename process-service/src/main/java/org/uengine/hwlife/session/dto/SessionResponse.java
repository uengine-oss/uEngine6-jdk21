package org.uengine.hwlife.session.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class SessionResponse {

    private String accessToken;
    private String sessionToken;
    private String loginType;
    private String sessionStatus;
    private String reasonCode;
    private String reasonMsg;
    /** ESB 응답코드 (01:정상, 00:오류) — 대체로그인 */
    private String rspnCodeVal;
    /** ESB 오류코드 (COME0303 등) — 대체로그인 실패 시 */
    private String rspnMsgeUniqid;
    /** ESB 오류메시지 — 대체로그인 실패 시 */
    private String rspnMsgeCntn;
    private Boolean displacedExisting;
    private SessionUserInfo user;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "Asia/Seoul")
    private Date lastLoginAt;
    private String lastLoginIp;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "Asia/Seoul")
    private Date loginAt;
    private String loginIp;

    private Integer idleTimeoutMinutes;
    private Long idleRemainingSeconds;
    private Boolean extendPrompt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "Asia/Seoul")
    private Date expiresAt;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonMsg() {
        return reasonMsg;
    }

    public void setReasonMsg(String reasonMsg) {
        this.reasonMsg = reasonMsg;
    }

    public String getRspnCodeVal() {
        return rspnCodeVal;
    }

    public void setRspnCodeVal(String rspnCodeVal) {
        this.rspnCodeVal = rspnCodeVal;
    }

    public String getRspnMsgeUniqid() {
        return rspnMsgeUniqid;
    }

    public void setRspnMsgeUniqid(String rspnMsgeUniqid) {
        this.rspnMsgeUniqid = rspnMsgeUniqid;
    }

    public String getRspnMsgeCntn() {
        return rspnMsgeCntn;
    }

    public void setRspnMsgeCntn(String rspnMsgeCntn) {
        this.rspnMsgeCntn = rspnMsgeCntn;
    }

    public Boolean getDisplacedExisting() {
        return displacedExisting;
    }

    public void setDisplacedExisting(Boolean displacedExisting) {
        this.displacedExisting = displacedExisting;
    }

    public SessionUserInfo getUser() {
        return user;
    }

    public void setUser(SessionUserInfo user) {
        this.user = user;
    }

    public Date getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Date lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public Date getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(Date loginAt) {
        this.loginAt = loginAt;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public Integer getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(Integer idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }

    public Long getIdleRemainingSeconds() {
        return idleRemainingSeconds;
    }

    public void setIdleRemainingSeconds(Long idleRemainingSeconds) {
        this.idleRemainingSeconds = idleRemainingSeconds;
    }

    public Boolean getExtendPrompt() {
        return extendPrompt;
    }

    public void setExtendPrompt(Boolean extendPrompt) {
        this.extendPrompt = extendPrompt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
}
