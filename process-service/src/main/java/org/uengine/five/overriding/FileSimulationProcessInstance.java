package org.uengine.five.overriding;

import java.util.Map;
import java.util.Optional;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.five.framework.FileSimulationStorage;
import org.uengine.five.serializers.BpmnXMLParser;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.processmanager.TransactionContext;
import org.uengine.webservices.worklist.WorkList;

public class FileSimulationProcessInstance extends CLOBProcessInstance {
    private static final WorkList FILE_WORK_LIST = new FileSimulationWorkList();

    public FileSimulationProcessInstance(ProcessDefinition definition, String instanceId, Map<?, ?> options)
            throws Exception {
        super(definition, instanceId, options);
    }

    @Override
    public WorkList getWorkList() {
        return FILE_WORK_LIST;
    }

    @Override
    protected Optional<ProcessInstanceEntity> findInstanceEntity(Long instanceId) throws Exception {
        return storage(instanceId).findInstance(instanceId);
    }

    @Override
    protected void saveInstanceEntity(ProcessInstanceEntity entity) throws Exception {
        storage(entity.getInstId()).saveInstance(entity);
    }

    @Override
    protected ProcessDefinition findTestDefinition(Long instanceId) throws Exception {
        ProcessDefinition registered = super.findTestDefinition(instanceId);
        if (registered != null || instanceId == null) return registered;

        String xml = storage(instanceId).getDefinitionXml(instanceId);
        if (xml == null) return null;

        ProcessDefinition definition = new BpmnXMLParser().parse(xml);
        String definitionId = getProcessInstanceEntity().getDefId();
        definition.setId(definitionId);
        if (definition.getName() == null) definition.setName(definitionId);
        return definition;
    }

    @Override
    public void beforeCommit(TransactionContext transaction) throws Exception {
        saveVariables();
        var entity = getProcessInstanceEntity();
        ((ProcessTransactionContext) transaction).getFileSimulationStorage(entity.getInstId()).saveInstance(entity);
    }

    @Override
    protected void afterProcessFinished(ProcessInstanceEntity instance, String status) {
        // File executions have no database work items or lifecycle events to reconcile.
    }

    @Override
    public boolean sendBroadcast(String eventType, Object payload) {
        return false;
    }

    private FileSimulationStorage storage(Long instanceId) throws Exception {
        return ProcessTransactionContext.getThreadLocalInstance().getFileSimulationStorage(instanceId);
    }
}
