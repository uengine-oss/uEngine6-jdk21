package org.uengine.five.analytics.etl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "BPM_DIM_PROCESS_DEF")
public class AnalyticsProcessDefinitionDimension {

    @Id
    @Column(length = 32)
    private String processKey;
    private String definitionId;
    private String definitionVersionId;
    private String definitionName;
    private String definitionPath;

    protected AnalyticsProcessDefinitionDimension() {
    }

    public AnalyticsProcessDefinitionDimension(String processKey, String definitionId,
                                                String definitionVersionId, String definitionName,
                                                String definitionPath) {
        this.processKey = processKey;
        this.definitionId = definitionId;
        this.definitionVersionId = definitionVersionId;
        this.definitionName = definitionName;
        this.definitionPath = definitionPath;
    }

    public String getProcessKey() { return processKey; }
    public String getDefinitionId() { return definitionId; }
    public String getDefinitionVersionId() { return definitionVersionId; }
    public String getDefinitionName() { return definitionName; }
    public String getDefinitionPath() { return definitionPath; }
}
