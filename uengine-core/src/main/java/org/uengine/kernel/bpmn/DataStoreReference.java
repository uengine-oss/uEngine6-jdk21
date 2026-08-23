package org.uengine.kernel.bpmn;

/**
 * BPMN DataStoreReference marker.
 *
 * <p>The database icon is a modeling reference only. SQL execution remains in
 * the linked {@link SQLTask}; this type exists so process definitions can be
 * deserialized without treating the reference as an executable SQL activity.</p>
 */
public class DataStoreReference extends DataStore {
}
