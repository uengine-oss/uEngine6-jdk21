package org.uengine.five.entity;

import jakarta.persistence.*;

/**
 * Created by uengine on 2017. 8. 1..
 */
@Entity
@Table(name = "BPM_ROLEMAPPING")
@SequenceGenerator(
    name = "rolemapping_seq_gen",
    sequenceName = "SEQ_BPM_ROLEMAPPING",
    allocationSize = 50
)
public class RoleMappingEntity {//implements RoleMappingDAO {

    @Id
    // @GeneratedValue(strategy = GenerationType.AUTO)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rolemapping_seq_gen")
    Long roleMappingId;

    @ManyToOne
    @JoinColumn(name="instId")
    ProcessInstanceEntity processInstance;
    public ProcessInstanceEntity getProcessInstance() {
        return processInstance;
    }
    public void setProcessInstance(ProcessInstanceEntity processInstance) {
        this.processInstance = processInstance;
    }

    @ManyToOne
    @JoinColumn(name="rootInstId")
    ProcessInstanceEntity rootProcessInstance;
    public ProcessInstanceEntity getRootProcessInstance() {
        return rootProcessInstance;
    }
    public void setRootProcessInstance(ProcessInstanceEntity rootProcessInstance) {
        this.rootProcessInstance = rootProcessInstance;
    }

    String roleName;

    String value;

    String endpoint;

    String resName;

    String groupId;

    Number assignType;

    String assignParam1;

    String dispatchParam1;

    Number dispatchOption;

    String policyId;
    String difficulty;
    String refId;

    public String getPolicyId() {
        return policyId;
    }
    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }
    public String getDifficulty() {
        return difficulty;
    }
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    public String getRefId() {
        return refId;
    }
    public void setRefId(String refId) {
        this.refId = refId;
    }

    public Long getRoleMappingId() {
        return roleMappingId;
    }

    public void setRoleMappingId(Long roleMappingId) {
        this.roleMappingId = roleMappingId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getResName() {
        return resName;
    }

    public void setResName(String resName) {
        this.resName = resName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Number getAssignType() {
        return assignType;
    }

    public void setAssignType(Number assignType) {
        this.assignType = assignType;
    }

    public String getAssignParam1() {
        return assignParam1;
    }

    public void setAssignParam1(String assignParam1) {
        this.assignParam1 = assignParam1;
    }

    public String getDispatchParam1() {
        return dispatchParam1;
    }

    public void setDispatchParam1(String dispatchParam1) {
        this.dispatchParam1 = dispatchParam1;
    }

    public Number getDispatchOption() {
        return dispatchOption;
    }

    public void setDispatchOption(Number dispatchOption) {
        this.dispatchOption = dispatchOption;
    }
}
