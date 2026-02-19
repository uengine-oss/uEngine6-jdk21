package org.uengine.five.audit;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.five.overriding.JPAProcessInstance;
import org.uengine.five.service.InstanceAuditRecorder;
import org.uengine.kernel.ProcessInstance;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * InstanceAuditRecorder의 AuditService 기반 구현.
 * DefinitionServiceUtil ↔ LocalFileDefinitionServiceUtil / SupabaseDefinitionServiceUtil 과 같은 패턴.
 */
@Component
public class AuditServiceInstanceAuditRecorder implements InstanceAuditRecorder {

    private final AuditService auditService;
    private final ObjectMapper valueMapper;

    @Autowired
    public AuditServiceInstanceAuditRecorder(AuditService auditService) {
        this.auditService = auditService != null ? auditService : new NoOpAuditService();
        this.valueMapper = new ObjectMapper();
    }

    @Override
    public void recordVariableChange(ProcessInstance instance, String varName, Serializable oldValue, Serializable newValue, String taskId) {
        Long rootInstId = null, instId = null;
        try {
            if (instance instanceof JPAProcessInstance) {
                ProcessInstance root = instance.getRootProcessInstance();
                if (root instanceof JPAProcessInstance) rootInstId = ((JPAProcessInstance) root).getProcessInstanceEntity().getInstId();
                instId = ((JPAProcessInstance) instance).getProcessInstanceEntity().getInstId();
            }
        } catch (Exception ignore) { }
        AuditEvent event = AuditEvent.of(AuditEventType.VARIABLE_CHANGE, rootInstId, instId)
                .withActor(org.uengine.five.spring.SecurityAwareServletFilter.getUserId())
                .withPayload("varName", varName)
                .withPayload("oldValue", valueToAuditString(oldValue))
                .withPayload("newValue", valueToAuditString(newValue));
        if (taskId != null) event.withPayload("taskId", taskId);
        auditService.record(event);
    }

    @Override
    public List<AuditEvent> listByRootInstanceId(Long rootInstId, int limit) {
        if (rootInstId == null) return Collections.emptyList();
        return auditService.listByRootInstanceId(rootInstId, limit > 0 ? limit : 500);
    }

    @Override
    public void recordTaskDelegation(Long rootInstId, Long instId, String tracingTag, String taskId,
            String fromEndpoint, String toEndpoint, boolean delegateOnlyForWorkitem, String actor) {
        AuditEvent event = AuditEvent.of(AuditEventType.TASK_DELEGATION, rootInstId, instId)
                .withActor(actor)
                .withTracingTag(tracingTag)
                .withPayload("taskId", taskId)
                .withPayload("fromEndpoint", fromEndpoint)
                .withPayload("toEndpoint", toEndpoint)
                .withPayload("delegateOnlyForWorkitem", delegateOnlyForWorkitem);
        auditService.record(event);
    }

    private String valueToAuditString(Serializable v) {
        if (v == null) return null;
        try {
            return valueMapper.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private static class NoOpAuditService implements AuditService {
        @Override public void record(AuditEvent event) { }
        @Override public List<AuditEvent> listByRootInstanceId(Long rootInstId, int limit) { return Collections.emptyList(); }
    }
}
