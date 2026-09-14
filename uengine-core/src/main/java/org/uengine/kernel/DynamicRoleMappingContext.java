package org.uengine.kernel;

import java.util.Map;

/**
 * Re-evaluates a persisted role mapping when process-variable-backed assignment
 * criteria may have changed.
 */
public interface DynamicRoleMappingContext {

    RoleMapping resolveRoleMapping(
            ProcessDefinition definition,
            ProcessInstance instance,
            String tracingTag,
            RoleMapping currentMapping,
            Map options) throws Exception;
}
