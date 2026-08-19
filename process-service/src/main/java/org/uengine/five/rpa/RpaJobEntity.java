package org.uengine.five.rpa;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * RPA 실행 Job. 서버 워커(Docker)와 클라이언트 에이전트(트레이 앱)가 폴링해 가져간다.
 */
@Entity
@Table(name = "RPA_JOB")
public class RpaJobEntity {

    /** 담당자가 [RPA 실행] 버튼을 누를 때까지 대기 — 폴링 대상이 아님 */
    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    @Id
    @Column(length = 40)
    String jobId;

    String instanceId;
    String tracingTag;
    String definitionId;
    String activityName;

    /** server | client */
    String mode;

    /** client 모드일 때 이 Job 을 수행할 사용자 endpoint */
    String targetUser;

    String status;

    /** Job 을 가져간 에이전트/워커 식별자 */
    String agentId;

    @Column(columnDefinition = "text")
    String script;

    @Column(columnDefinition = "text")
    String inputJson;

    @Column(columnDefinition = "text")
    String resultJson;

    @Column(columnDefinition = "text")
    String logText;

    @Column(columnDefinition = "text")
    String error;

    int timeoutSeconds;

    Date createdDate;
    Date claimedDate;
    Date completedDate;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTracingTag() {
        return tracingTag;
    }

    public void setTracingTag(String tracingTag) {
        this.tracingTag = tracingTag;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getClaimedDate() {
        return claimedDate;
    }

    public void setClaimedDate(Date claimedDate) {
        this.claimedDate = claimedDate;
    }

    public Date getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Date completedDate) {
        this.completedDate = completedDate;
    }
}
