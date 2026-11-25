package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uengine.contexts.UserContext;
import org.uengine.five.ProcessServiceWebConfig;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.five.overriding.CLOBProcessInstance;
import org.uengine.five.overriding.FileSimulationProcessInstance;
import org.uengine.kernel.KeyedParameter;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.UEngineException;
import org.uengine.modeling.resource.DefaultResource;
import org.uengine.modeling.resource.LocalFileStorage;

class InstanceServiceInitiationPermissionTest {
    @TempDir
    Path simulationDirectory;

    @Test
    void recognizesWrappedInitiationPermissionFailure() {
        Exception wrapped = new Exception(new UEngineException(
                "You (hong) are not permitted to initiate this process."));

        assertTrue(InstanceServiceImpl.isInitiationPermissionFailure(wrapped));
    }

    @Test
    void leavesOtherExecutionFailuresUnchanged() {
        assertFalse(InstanceServiceImpl.isInitiationPermissionFailure(
                new IllegalStateException("definition could not be loaded")));
    }

    @Test
    void factorySelectsFileInstanceForNewSimulationsAndReopensTheirSnapshots() throws Exception {
        String property = "uengine.definition.basePath";
        String previous = System.getProperty(property);
        UserContext user = UserContext.getThreadLocalInstance();
        String previousUser = user.getUserId();
        System.setProperty(property, simulationDirectory.toString());
        user.setUserId("file-test-owner");
        try {
            var factory = new ProcessServiceWebConfig();
            var definition = new ProcessDefinition();
            definition.setId("file-instance-factory-test");
            definition.setName("File instance factory test");
            assertEquals(CLOBProcessInstance.class, factory.processInstance(definition, null, null).getClass());

            String id;
            String taskId;
            var write = new ProcessTransactionContext() {};
            try {
                write.beginFileSimulation();
                assertEquals(CLOBProcessInstance.class, factory.processInstance(null, "7201", null).getClass());
                var instance = (FileSimulationProcessInstance) factory.processInstance(definition, null, null);
                instance.init();
                id = instance.getInstanceId();
                var workList = instance.getWorkList();
                KeyedParameter[] parameters = {
                    new KeyedParameter(KeyedParameter.INSTANCEID, id),
                    new KeyedParameter(KeyedParameter.ROOTINSTANCEID, id),
                    new KeyedParameter("assignType", 0),
                    new KeyedParameter("resourceName", "Test owner")
                };
                RemoteException mismatch = assertThrows(RemoteException.class,
                        () -> workList.addWorkItem("1", null, parameters, write));
                assertTrue(mismatch.getCause() instanceof IllegalArgumentException);
                taskId = workList.reserveWorkItem(null, parameters, write);
                var storage = write.getFileSimulationStorage(Long.valueOf(id));
                var task = storage.findWorkItem(Long.valueOf(taskId)).orElseThrow();
                assertEquals("RESERVED", task.getStatus());
                assertEquals(taskId, workList.addWorkItem(taskId, null, parameters, write));
                assertEquals("NEW", task.getStatus());
                workList.updateWorkItem(taskId, null, new KeyedParameter[] {
                    new KeyedParameter(KeyedParameter.DEFAULT_STATUS, "DRAFT"),
                    new KeyedParameter("saveDate", new Date(1_000L))
                }, write);
                assertEquals("DRAFT", task.getStatus());
                workList.cancelWorkItem(taskId, new KeyedParameter[] {new KeyedParameter("status", "skipped")}, write);
                assertEquals("SKIPPED", task.getStatus());
                workList.compensateWorkItem(taskId, null, write);
                assertEquals("COMPENSATED", task.getStatus());
                workList.completeWorkItem(taskId, null, write);
                write.commit();
            } finally {
                write.rollback();
            }

            var read = new ProcessTransactionContext() {};
            try {
                var reopened = (FileSimulationProcessInstance) factory.processInstance(null, id, null);
                reopened.init();
                assertEquals(id, reopened.getInstanceId());
                assertEquals(definition.getId(), reopened.getProcessInstanceEntity().getDefId());
                var task = read.getFileSimulationStorage(Long.valueOf(id)).findWorkItem(Long.valueOf(taskId)).orElseThrow();
                assertEquals("COMPLETED", task.getStatus());
                assertEquals(1_000L, task.getSaveDate().getTime());
                assertTrue(task.getEndDate().after(task.getSaveDate()));
            } finally {
                read.rollback();
            }
        } finally {
            user.setUserId(previousUser);
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void fileSimulationCommitsExecutionAndRollsBackWithoutReplacingPreviousSnapshot() throws Exception {
        String property = "uengine.definition.basePath";
        String previous = System.getProperty(property);
        UserContext user = UserContext.getThreadLocalInstance();
        String previousUser = user.getUserId();
        System.setProperty(property, simulationDirectory.toString());
        user.setUserId("file-test-owner");
        try {
            var transaction = new ProcessTransactionContext() {};
            transaction.beginFileSimulation();
            var storage = transaction.getFileSimulationStorage(null);
            var process = new ProcessInstanceEntity();
            process.setDefId("old-api-definition");
            process.setStatus("Running");
            process.setVarLob(new byte[] { 42 });
            storage.saveInstance(process);
            Long id = process.getInstId();
            var task = new WorklistEntity();
            task.setInstId(id);
            task.setRootInstId(id);
            task.setStatus("RUNNING");
            storage.saveWorkItem(task);
            storage.saveDefinitionXml(id, "<definition/>");
            Path instancesPath = simulationDirectory.resolve("definition/simulation/instances");
            Path dayPath = instancesPath.resolve(java.time.LocalDate.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("uuuu/MM/dd")));
            try (var snapshots = Files.newDirectoryStream(dayPath, id + "_*")) {
                assertFalse(snapshots.iterator().hasNext());
            }
            transaction.commit();
            try (var snapshots = Files.newDirectoryStream(dayPath, id + "_*")) {
                Path snapshot = snapshots.iterator().next();
                assertTrue(Files.isRegularFile(snapshot));
                assertTrue(snapshot.getFileName().toString().matches(id + "_[0-9]+"));
                assertTrue(Files.readString(snapshot).contains("FileSimulationStorage"));
            }

            var next = new ProcessTransactionContext() {};
            var loaded = next.getFileSimulationStorage(id);
            assertEquals("<definition/>", loaded.getDefinitionXml(id));
            assertEquals(42, loaded.findInstance(id).orElseThrow().getVarLob()[0]);
            var loadedTask = loaded.findWorkItem(task.getTaskId()).orElseThrow();
            loadedTask.setStatus("COMPLETED");
            loaded.saveWorkItem(loadedTask);
            next.rollback();

            var read = new ProcessTransactionContext() {};
            try {
                assertEquals("RUNNING", read.getFileSimulationStorage(id).findWorkItem(task.getTaskId()).orElseThrow().getStatus());
            } finally {
                read.rollback();
            }
        } finally {
            user.setUserId(previousUser);
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
    @Test
    void ordinaryResourcesCannotReadOrOverwriteSimulationFiles() throws Exception {
        var storage = new LocalFileStorage();
        storage.setBasePath(simulationDirectory.toString());
        storage.reserveSubtree("definition/simulation");
        var ordinary = new DefaultResource("definitions/sample.bpmn");
        storage.save(ordinary, "sample");
        assertTrue(storage.exists(ordinary));

        Path snapshot = simulationDirectory.resolve("definition/simulation/instances/sample");
        Files.createDirectories(snapshot.getParent());
        Files.writeString(snapshot, "private snapshot");
        var reserved = new DefaultResource("definition/simulation/instances/sample");
        assertThrows(SecurityException.class, () -> storage.getInputStream(reserved));
        assertThrows(SecurityException.class, () -> storage.save(reserved, "overwrite"));
        var parent = new DefaultResource("definition");
        assertThrows(SecurityException.class, () -> storage.delete(parent));
        assertThrows(SecurityException.class, () -> storage.copy(parent, "copied-definition"));
        assertEquals("private snapshot", Files.readString(snapshot));
    }
}
