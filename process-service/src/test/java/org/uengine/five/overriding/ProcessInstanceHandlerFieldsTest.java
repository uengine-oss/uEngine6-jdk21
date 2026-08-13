package org.uengine.five.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.kernel.RoleMapping;

class ProcessInstanceHandlerFieldsTest {

    @Test
    void initializesFirstAndCurrentHandlerFieldsFromRoleMappingGroupName() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        RoleMapping handler = handler("hong", "Hong Gil Dong", "manager", "ORG001");

        ProcessInstanceHandlerFields.initialize(instance, handler);

        assertEquals("hong", instance.getInitEp());
        assertEquals("Hong Gil Dong", instance.getInitRsNm());
        assertEquals("ORG001", instance.getInitGroupCd());
        assertEquals("hong", instance.getCurrEp());
        assertEquals("Hong Gil Dong", instance.getCurrRsNm());
        assertEquals("ORG001", instance.getCurrGroupCd());
    }

    @Test
    void doesNotUseScopeAsHandlerGroupCode() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        RoleMapping handler = handler(null, null, "manager", "ORG001");

        ProcessInstanceHandlerFields.initialize(instance, handler);

        assertEquals("ORG001", instance.getInitGroupCd());
        assertEquals("ORG001", instance.getCurrGroupCd());
    }

    private static RoleMapping handler(
            String endpoint,
            String resourceName,
            String scope,
            String groupName) {
        RoleMapping roleMapping = RoleMapping.create();
        roleMapping.setEndpoint(endpoint);
        roleMapping.setResourceName(resourceName);
        roleMapping.setScope(scope);
        roleMapping.setGroupName(groupName);
        return roleMapping;
    }
}
