package org.uengine.five.overriding;

import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.five.framework.FileSimulationStorage;

final class FileSimulationWorkList extends JPAWorkList {
    @Override
    protected void prepareWorkItemStorage(Long instanceId) throws Exception {
        storage(instanceId);
    }

    @Override
    protected WorklistEntity findWorkItem(Long taskId) throws Exception {
        return storage(taskId).findWorkItem(taskId).orElseThrow();
    }

    @Override
    protected WorklistEntity findReservedWorkItem(Long instanceId, Long taskId) throws Exception {
        // A reservation can only be reused inside the new work item's execution file.
        return storage(instanceId).findWorkItem(taskId).orElseThrow();
    }

    @Override
    protected WorklistEntity createWorkItem() {
        return new WorklistEntity();
    }

    @Override
    protected void saveWorkItem(WorklistEntity workItem) throws Exception {
        storage(workItem.getInstId()).saveWorkItem(workItem);
    }

    @Override
    protected void applyDatabaseUpdates(DatabaseUpdate update) {
        // File executions do not change operational assignments or publish lifecycle events.
    }

    private FileSimulationStorage storage(Long id) throws Exception {
        return ProcessTransactionContext.getThreadLocalInstance().getFileSimulationStorage(id);
    }
}
