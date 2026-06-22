package org.uengine.five.dto;

import java.io.Serializable;

// import lombok.Data;

//@Data
public class ProcessExecutionCommand implements Serializable {
    private String processDefinitionId;

    private String instanceName;
    private boolean simulation;

    private RoleMapping[] roleMappings;
    private ProcessVariableValue[] processVariableValues;

    private String group;

    private String correlationKeyValue;

    private String definitionXml;

    /**
     * 시작이벤트가 외부 inbox 이벤트로 트리거됐을 때 원본 페이로드.
     * InstanceServiceImpl.start() 가 인스턴스 생성 직후·execute() 직전에
     * 시작이벤트의 eventSynchronization.mappingContext 를 이 페이로드 대상으로 실행한다.
     */
    private java.util.Map<String, Object> startEventPayload;

    public java.util.Map<String, Object> getStartEventPayload() {
        return startEventPayload;
    }

    public void setStartEventPayload(java.util.Map<String, Object> startEventPayload) {
        this.startEventPayload = startEventPayload;
    }

    public String getDefinitionXml() {
        return definitionXml;
    }

    public void setDefinitionXml(String definitionXml) {
        this.definitionXml = definitionXml;
    }

    public String getCorrelationKeyValue() {
        return correlationKeyValue;
    }

    public void setCorrelationKeyValue(String correlationKeyValue) {
        this.correlationKeyValue = correlationKeyValue;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public RoleMapping[] getRoleMappings() {
        return roleMappings;
    }

    public void setRoleMappings(RoleMapping[] roleMappings) {
        this.roleMappings = roleMappings;
    }

    public ProcessVariableValue[] getProcessVariableValues() {
        return processVariableValues;
    }

    public void setProcessVariableValues(ProcessVariableValue[] processVariableValues) {
        this.processVariableValues = processVariableValues;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public boolean getSimulation() {
        return simulation;
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

}
