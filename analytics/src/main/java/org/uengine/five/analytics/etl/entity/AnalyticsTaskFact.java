package org.uengine.five.analytics.etl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

@Entity
@Table(name = "BPM_FACT_TASK")
public class AnalyticsTaskFact {

    @Id
    private Long taskId;
    private Long processInstanceId;
    private Long rootProcessInstanceId;
    @Column(length = 32)
    private String processKey;
    @Column(length = 32)
    private String activityKey;
    @Column(length = 32)
    private String actorKey;
    private Integer startDateKey;
    private Integer endDateKey;
    @Temporal(TemporalType.TIMESTAMP)
    private Date startedAt;
    @Temporal(TemporalType.TIMESTAMP)
    private Date finishedAt;
    private Long durationSeconds;
    private Long waitFromPreviousSeconds;
    private Long leadFromProcessSeconds;
    private String status;
    private String decision;
    @Column(columnDefinition = "text")
    private String decisionReason;
    private Integer priority;
    private Boolean delegated;
    private Boolean humanTask;
    private Boolean automatedTask;
    private Boolean reworkTask;
    @Temporal(TemporalType.TIMESTAMP)
    private Date sourceUpdatedAt;

    public AnalyticsTaskFact() {
    }

    public Long getTaskId() { return taskId; }
    public Long getProcessInstanceId() { return processInstanceId; }
    public Long getRootProcessInstanceId() { return rootProcessInstanceId; }
    public String getProcessKey() { return processKey; }
    public String getActivityKey() { return activityKey; }
    public String getActorKey() { return actorKey; }
    public Integer getStartDateKey() { return startDateKey; }
    public Integer getEndDateKey() { return endDateKey; }
    public Date getStartedAt() { return startedAt; }
    public Date getFinishedAt() { return finishedAt; }
    public Long getDurationSeconds() { return durationSeconds; }
    public Long getWaitFromPreviousSeconds() { return waitFromPreviousSeconds; }
    public Long getLeadFromProcessSeconds() { return leadFromProcessSeconds; }
    public String getStatus() { return status; }
    public String getDecision() { return decision; }
    public String getDecisionReason() { return decisionReason; }
    public Integer getPriority() { return priority; }
    public Boolean getDelegated() { return delegated; }
    public Boolean getHumanTask() { return humanTask; }
    public Boolean getAutomatedTask() { return automatedTask; }
    public Boolean getReworkTask() { return reworkTask; }
    public Date getSourceUpdatedAt() { return sourceUpdatedAt; }

    public void setTaskId(Long value) { taskId = value; }
    public void setProcessInstanceId(Long value) { processInstanceId = value; }
    public void setRootProcessInstanceId(Long value) { rootProcessInstanceId = value; }
    public void setProcessKey(String value) { processKey = value; }
    public void setActivityKey(String value) { activityKey = value; }
    public void setActorKey(String value) { actorKey = value; }
    public void setStartDateKey(Integer value) { startDateKey = value; }
    public void setEndDateKey(Integer value) { endDateKey = value; }
    public void setStartedAt(Date value) { startedAt = value; }
    public void setFinishedAt(Date value) { finishedAt = value; }
    public void setDurationSeconds(Long value) { durationSeconds = value; }
    public void setWaitFromPreviousSeconds(Long value) { waitFromPreviousSeconds = value; }
    public void setLeadFromProcessSeconds(Long value) { leadFromProcessSeconds = value; }
    public void setStatus(String value) { status = value; }
    public void setDecision(String value) { decision = value; }
    public void setDecisionReason(String value) { decisionReason = value; }
    public void setPriority(Integer value) { priority = value; }
    public void setDelegated(Boolean value) { delegated = value; }
    public void setHumanTask(Boolean value) { humanTask = value; }
    public void setAutomatedTask(Boolean value) { automatedTask = value; }
    public void setReworkTask(Boolean value) { reworkTask = value; }
    public void setSourceUpdatedAt(Date value) { sourceUpdatedAt = value; }
}
