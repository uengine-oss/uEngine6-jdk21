package org.uengine.five.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.five.framework.FileSimulationStorage;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;

/** Selects the persistence implementation for operations entered with an execution or work item ID. */
public final class ExecutionRecords {
    private ExecutionRecords() {}

    static void begin(boolean simulation) throws Exception {
        if (simulation) ProcessTransactionContext.getThreadLocalInstance().beginFileSimulation();
    }

    static Records forId(Long id, ProcessInstanceRepository instances, WorklistRepository workItems) {
        if (!ProcessTransactionContext.isFileSimulationId(id)) {
            return new DatabaseRecords(id, instances, workItems);
        }
        return new FileRecords(id, openFile(id));
    }

    static void requireAccess(Long id) {
        if (ProcessTransactionContext.isFileSimulationId(id)) openFile(id);
    }

    private static FileSimulationStorage openFile(Long id) {
        try {
            return ProcessTransactionContext.getThreadLocalInstance().getFileSimulationStorage(id);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "시뮬레이션 실행을 찾을 수 없습니다.", error);
        }
    }

    public static void requireSameStorage(Long instanceId, Long taskId) {
        if (ProcessTransactionContext.isFileSimulationId(instanceId)
                != ProcessTransactionContext.isFileSimulationId(taskId)) {
            throw new IllegalArgumentException("Reserved work item belongs to a different execution store");
        }
    }

    public static void runDatabaseEffect(Long id, Runnable effect) {
        if (!ProcessTransactionContext.isFileSimulationId(id)) effect.run();
    }

    public static void runDatabaseEffect(String id, Runnable effect) {
        Long numericId;
        try {
            numericId = Long.valueOf(id);
        } catch (NumberFormatException ignored) {
            numericId = null;
        }
        runDatabaseEffect(numericId, effect);
    }

    interface Records {
        List<WorklistEntity> running();
        List<WorklistEntity> forInstance();
        List<WorklistEntity> forRoot();
        Optional<WorklistEntity> workItem(boolean lock);
        void delete();

        default void saveDefinitionXml(String xml) {}
    }

    private record DatabaseRecords(Long id, ProcessInstanceRepository instances, WorklistRepository workItems)
            implements Records {
        @Override
        public List<WorklistEntity> running() {
            return workItems.findCurrentWorkItemByInstId(id);
        }

        @Override
        public List<WorklistEntity> forInstance() {
            return workItems.findWorkListByInstId(id);
        }

        @Override
        public List<WorklistEntity> forRoot() {
            return instances.findAllWorklistsByRootInstId(id);
        }

        @Override
        public Optional<WorklistEntity> workItem(boolean lock) {
            return lock ? workItems.findByIdForUpdate(id) : workItems.findById(id);
        }

        @Override
        public void delete() {
            try {
                List<WorklistEntity> history = forRoot();
                if (history != null && !history.isEmpty()) workItems.deleteAll(history);
            } catch (Exception ignored) {
            }
            if (instances.existsById(id)) instances.deleteById(id);
        }
    }

    private record FileRecords(Long id, FileSimulationStorage storage) implements Records {
        private static final Comparator<WorklistEntity> START_ORDER = Comparator
                .comparing(WorklistEntity::getStartDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(WorklistEntity::getTaskId);

        @Override
        public List<WorklistEntity> running() {
            return storage.findWorkItems(id).stream()
                    .filter(item -> id.equals(item.getRootInstId()))
                    .filter(item -> "NEW".equals(item.getStatus()) || "RUNNING".equals(item.getStatus()))
                    .sorted(START_ORDER.reversed()).toList();
        }

        @Override
        public List<WorklistEntity> forInstance() {
            return storage.findWorkItems(id).stream()
                    .filter(item -> id.equals(item.getInstId()))
                    .sorted(Comparator.comparing(WorklistEntity::getEndDate,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        @Override
        public List<WorklistEntity> forRoot() {
            return storage.findWorkItems(id).stream()
                    .filter(item -> id.equals(item.getRootInstId()))
                    .sorted(START_ORDER).toList();
        }

        @Override
        public Optional<WorklistEntity> workItem(boolean lock) {
            return storage.findWorkItem(id);
        }

        @Override
        public void delete() {
            storage.delete();
        }

        @Override
        public void saveDefinitionXml(String xml) {
            storage.saveDefinitionXml(id, xml);
        }
    }
}
