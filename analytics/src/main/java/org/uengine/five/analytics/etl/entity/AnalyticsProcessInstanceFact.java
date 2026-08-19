package org.uengine.five.analytics.etl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

@Entity
@Table(name = "BPM_FACT_PROC_INST")
public class AnalyticsProcessInstanceFact {

    @Id
    private Long processInstanceId;
    private Long rootProcessInstanceId;
    @Column(length = 32)
    private String processKey;
    private Integer startDateKey;
    private Integer endDateKey;
    @Temporal(TemporalType.TIMESTAMP)
    private Date startedAt;
    @Temporal(TemporalType.TIMESTAMP)
    private Date finishedAt;
    private Long durationSeconds;
    private String status;
    private Boolean deleted;
    private Boolean subprocess;
    private Boolean eventHandler;
    private String initiator;
    private String initiatorGroupCode;
    private Integer totalTaskCount;
    private Integer completedTaskCount;
    private Integer activeTaskCount;
    private Integer cancelledTaskCount;
    private Integer humanTaskCount;
    private Integer automatedTaskCount;
    private Integer reworkTaskCount;
    @Temporal(TemporalType.TIMESTAMP)
    private Date sourceUpdatedAt;

    public AnalyticsProcessInstanceFact() {
    }

    public Long getProcessInstanceId() { return processInstanceId; }
    public Long getRootProcessInstanceId() { return rootProcessInstanceId; }
    public String getProcessKey() { return processKey; }
    public Integer getStartDateKey() { return startDateKey; }
    public Integer getEndDateKey() { return endDateKey; }
    public Date getStartedAt() { return startedAt; }
    public Date getFinishedAt() { return finishedAt; }
    public Long getDurationSeconds() { return durationSeconds; }
    public String getStatus() { return status; }
    public Boolean getDeleted() { return deleted; }
    public Boolean getSubprocess() { return subprocess; }
    public Boolean getEventHandler() { return eventHandler; }
    public String getInitiator() { return initiator; }
    public String getInitiatorGroupCode() { return initiatorGroupCode; }
    public Integer getTotalTaskCount() { return totalTaskCount; }
    public Integer getCompletedTaskCount() { return completedTaskCount; }
    public Integer getActiveTaskCount() { return activeTaskCount; }
    public Integer getCancelledTaskCount() { return cancelledTaskCount; }
    public Integer getHumanTaskCount() { return humanTaskCount; }
    public Integer getAutomatedTaskCount() { return automatedTaskCount; }
    public Integer getReworkTaskCount() { return reworkTaskCount; }
    public Date getSourceUpdatedAt() { return sourceUpdatedAt; }

    public void setProcessInstanceId(Long value) { processInstanceId = value; }
    public void setRootProcessInstanceId(Long value) { rootProcessInstanceId = value; }
    public void setProcessKey(String value) { processKey = value; }
    public void setStartDateKey(Integer value) { startDateKey = value; }
    public void setEndDateKey(Integer value) { endDateKey = value; }
    public void setStartedAt(Date value) { startedAt = value; }
    public void setFinishedAt(Date value) { finishedAt = value; }
    public void setDurationSeconds(Long value) { durationSeconds = value; }
    public void setStatus(String value) { status = value; }
    public void setDeleted(Boolean value) { deleted = value; }
    public void setSubprocess(Boolean value) { subprocess = value; }
    public void setEventHandler(Boolean value) { eventHandler = value; }
    public void setInitiator(String value) { initiator = value; }
    public void setInitiatorGroupCode(String value) { initiatorGroupCode = value; }
    public void setTotalTaskCount(Integer value) { totalTaskCount = value; }
    public void setCompletedTaskCount(Integer value) { completedTaskCount = value; }
    public void setActiveTaskCount(Integer value) { activeTaskCount = value; }
    public void setCancelledTaskCount(Integer value) { cancelledTaskCount = value; }
    public void setHumanTaskCount(Integer value) { humanTaskCount = value; }
    public void setAutomatedTaskCount(Integer value) { automatedTaskCount = value; }
    public void setReworkTaskCount(Integer value) { reworkTaskCount = value; }
    public void setSourceUpdatedAt(Date value) { sourceUpdatedAt = value; }
}
