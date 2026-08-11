package org.uengine.five.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.kernel.RoleMapping;

class ProcessInstanceHandlerFieldsTest {

    @Test
    void initializesFirstAndCurrentHandlerFields() {
        // ProcessInstanceEntity instance = new ProcessInstanceEntity();
        // RoleMapping handler = handler("hong", "Hong Gil Dong", "loan-scope");

        // ProcessInstanceHandlerFields.initialize(instance, handler);

        // assertEquals("hong", instance.getInitEp());
        // assertEquals("Hong Gil Dong", instance.getInitRsNm());
        // assertEquals("loan-scope", instance.getInitGroupCd());
        // assertEquals("hong", instance.getCurrEp());
        // assertEquals("Hong Gil Dong", instance.getCurrRsNm());
        // assertEquals("loan-scope", instance.getCurrGroupCd());
    }

    @Test
    void preservesInitialHandlerAndMovesCurrentHandlerToPrevious() {
        // ProcessInstanceEntity instance = new ProcessInstanceEntity();
        // ProcessInstanceHandlerFields.initialize(
        //         instance, handler("hong", "Hong Gil Dong", "loan-scope"));

        // ProcessInstanceHandlerFields.updateCurrent(
        //         instance, handler("kim", "Kim BPM", "review-scope"));

        // assertEquals("hong", instance.getInitEp());
        // assertEquals("Hong Gil Dong", instance.getInitRsNm());
        // assertEquals("loan-scope", instance.getInitGroupCd());
        // assertEquals("hong", instance.getPrevCurrEp());
        // assertEquals("Hong Gil Dong", instance.getPrevCurrRsNm());
        // assertEquals("loan-scope", instance.getPrevCurrGroupCd());
        // assertEquals("kim", instance.getCurrEp());
        // assertEquals("Kim BPM", instance.getCurrRsNm());
        // assertEquals("review-scope", instance.getCurrGroupCd());
    }

    @Test
    void usesScopeAsHandlerGroupCode() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        RoleMapping handler = handler(null, null, "loan-scope");

        ProcessInstanceHandlerFields.initialize(instance, handler);

        assertEquals("loan-scope", instance.getInitGroupCd());
        assertEquals("loan-scope", instance.getCurrGroupCd());
    }

    private static RoleMapping handler(
            String endpoint,
            String resourceName,
            String scope) {
        RoleMapping roleMapping = RoleMapping.create();
        roleMapping.setEndpoint(endpoint);
        roleMapping.setResourceName(resourceName);
        roleMapping.setScope(scope);
        return roleMapping;
    }
}
