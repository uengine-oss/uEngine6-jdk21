package org.uengine.five.overriding;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.kernel.RoleMapping;

public final class ProcessInstanceHandlerFields {

    private ProcessInstanceHandlerFields() {
    }

    public static void initialize(ProcessInstanceEntity instance, RoleMapping roleMapping) {
        if (instance == null || roleMapping == null) {
            return;
        }

        if (!hasText(instance.getInitEp())) {
            instance.setInitEp(roleMapping.getEndpoint());
        }
        if (!hasText(instance.getInitRsNm())) {
            instance.setInitRsNm(roleMapping.getResourceName());
        }
        if (!hasText(instance.getInitGroupCd())) {
            instance.setInitGroupCd(resolveGroup(roleMapping));
        }

        instance.setPrevCurrEp("");
        instance.setPrevCurrRsNm("");
        instance.setPrevCurrGroupCd("");
        instance.setCurrEp(roleMapping.getEndpoint());
        instance.setCurrRsNm(roleMapping.getResourceName());
        instance.setCurrGroupCd(resolveGroup(roleMapping));
    }

    public static String resolveGroup(RoleMapping roleMapping) {
        if (roleMapping == null) {
            return null;
        }
        return hasText(roleMapping.getScope()) ? roleMapping.getScope() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
    }
}
