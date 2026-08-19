package org.uengine.five.analytics.etl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "BPM_DIM_ACTOR")
public class AnalyticsActorDimension {

    @Id
    @Column(length = 32)
    private String actorKey;
    private String endpoint;
    private String resourceName;
    private String groupCode;
    private String roleName;

    protected AnalyticsActorDimension() {
    }

    public AnalyticsActorDimension(String actorKey, String endpoint, String resourceName,
                                   String groupCode, String roleName) {
        this.actorKey = actorKey;
        this.endpoint = endpoint;
        this.resourceName = resourceName;
        this.groupCode = groupCode;
        this.roleName = roleName;
    }

    public String getActorKey() { return actorKey; }
    public String getEndpoint() { return endpoint; }
    public String getResourceName() { return resourceName; }
    public String getGroupCode() { return groupCode; }
    public String getRoleName() { return roleName; }
}
