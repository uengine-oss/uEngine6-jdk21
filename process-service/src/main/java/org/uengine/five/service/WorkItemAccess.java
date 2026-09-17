package org.uengine.five.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.WorklistEntity;

/** Uses the authenticated caller, independently of the IAM provider. */
final class WorkItemAccess {
    private WorkItemAccess() {}

    static void requireVisible(WorklistEntity work, String actor) {
        UserContext user = UserContext.getThreadLocalInstance();
        if (actor == null || actor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }
        if (actor.equals(work.getEndpoint())) return;
        boolean group = empty(work.getGroupCd()) || contains(user.getGroups(), work.getGroupCd());
        boolean scope = empty(work.getScope()) || contains(user.getScopes(), work.getScope());
        boolean legacyRole = contains(user.getScopes(), work.getEndpoint());
        boolean unclaimed = empty(work.getEndpoint()) && Integer.valueOf(1).equals(work.getDispatchOption())
                && (!empty(work.getGroupCd()) || !empty(work.getScope()));
        if (!group || !scope || !(legacyRole || unclaimed)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission for this work item");
        }
    }

    private static boolean contains(List<String> values, String value) {
        return value != null && values != null && values.contains(value);
    }

    private static boolean empty(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}
