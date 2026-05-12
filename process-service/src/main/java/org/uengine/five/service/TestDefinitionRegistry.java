package org.uengine.five.service;

import java.util.concurrent.ConcurrentHashMap;

import org.uengine.kernel.ProcessDefinition;

/**
 * In-memory registry for ProcessDefinitions parsed from raw XML supplied in
 * ProcessExecutionCommand.definitionXml (test-with-unsaved-BPMN flow).
 *
 * Keyed by instance id. Populated when an instance is started from in-memory
 * XML; consulted by JPAProcessInstance.getProcessDefinition before falling back
 * to disk; removed on deleteInstance.
 *
 * NOTE: Process-local (static) — fine for the dev/test simulation use case
 * the frontend exercises (always paired with cleanup via DELETE /instance/{id}).
 */
public class TestDefinitionRegistry {

    private static final ConcurrentHashMap<String, ProcessDefinition> MAP = new ConcurrentHashMap<>();

    public static void put(String instanceId, ProcessDefinition definition) {
        if (instanceId == null || definition == null) return;
        MAP.put(instanceId, definition);
    }

    public static ProcessDefinition get(String instanceId) {
        if (instanceId == null) return null;
        return MAP.get(instanceId);
    }

    public static void remove(String instanceId) {
        if (instanceId == null) return;
        MAP.remove(instanceId);
    }
}
