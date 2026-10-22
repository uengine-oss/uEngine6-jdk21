package org.uengine.hwlife.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.util.Date;

/**
 * 사용자 세션 및 로그인 시도(성공·실패) 이력.
 *
 * <p>ACTIVE 세션과 FAIL 이력을 동일 테이블에 저장한다.</p>
 */
@Entity
@Table(name = "BPM_USER_SESSION")
@SequenceGenerator(
        name = "user_session_seq_gen",
        sequenceName = "SEQ_BPM_USER_SESSION",
        allocationSize = 50
)
public class UserSessionEntity {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LOGGED_OUT = "LOGGED_OUT";
    public static final String STATUS_DISPLACED = "DISPLACED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_FAIL = "FAIL";

    public static final String LOGIN_TYPE_SSO = "SSO";
    public static final String LOGIN_TYPE_ALTERNATE = "ALTERNATE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_session_seq_gen")
    private Long sessId;

    @Column(nullable = false)
    private String result;

    private String status;
    private String sessionToken;
    private String userId;
    private String userName;

    @Column(nullable = false)
    private String loginType;

    private String reasonCode;
    private String reasonMsg;
    private String loginIp;
    private String lastSeenIp;

    @Column(nullable = false)
    private Date loginAt;

    private Date lastSeenAt;
    private Date logoutAt;
    private Integer idleTimeoutMinutes;

    @Column(columnDefinition = "TEXT")
    private String userSnapshotJson;

    @PrePersist
    void onCreate() {
        Date now = new Date();
        if (loginAt == null) {
            loginAt = now;
        }
        if (RESULT_SUCCESS.equals(result) && lastSeenAt == null) {
            lastSeenAt = loginAt;
        }
    }

    public Long getSessId() {
        return sessId;
    }

    public void setSessId(Long sessId) {
        this.sessId = sessId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
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

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getLastSeenIp() {
        return lastSeenIp;
    }

    public void setLastSeenIp(String lastSeenIp) {
        this.lastSeenIp = lastSeenIp;
    }

    public Date getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(Date loginAt) {
        this.loginAt = loginAt;
    }

    public Date getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Date lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Date getLogoutAt() {
        return logoutAt;
    }

    public void setLogoutAt(Date logoutAt) {
        this.logoutAt = logoutAt;
    }

    public Integer getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(Integer idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }

    public String getUserSnapshotJson() {
        return userSnapshotJson;
    }

    public void setUserSnapshotJson(String userSnapshotJson) {
        this.userSnapshotJson = userSnapshotJson;
    }
}
