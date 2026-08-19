package org.uengine.five.analytics.etl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "BPM_DIM_ACTIVITY")
public class AnalyticsActivityDimension {

    @Id
    @Column(length = 32)
    private String activityKey;
    @Column(length = 32)
    private String processKey;
    private String tracingTag;
    private String absoluteTracingTag;
    private String activityName;
    private String activityType;
    private String tool;

    protected AnalyticsActivityDimension() {
    }

    public AnalyticsActivityDimension(String activityKey, String processKey, String tracingTag,
                                      String absoluteTracingTag, String activityName,
                                      String activityType, String tool) {
        this.activityKey = activityKey;
        this.processKey = processKey;
        this.tracingTag = tracingTag;
        this.absoluteTracingTag = absoluteTracingTag;
        this.activityName = activityName;
        this.activityType = activityType;
        this.tool = tool;
    }

    public String getActivityKey() { return activityKey; }
    public String getProcessKey() { return processKey; }
    public String getTracingTag() { return tracingTag; }
    public String getAbsoluteTracingTag() { return absoluteTracingTag; }
    public String getActivityName() { return activityName; }
    public String getActivityType() { return activityType; }
    public String getTool() { return tool; }
}
