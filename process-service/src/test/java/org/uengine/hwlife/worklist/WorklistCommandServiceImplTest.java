package org.uengine.hwlife.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.RoleMappingCommand;
import org.uengine.five.service.InstanceServiceImpl;

class WorklistCommandServiceImplTest {

    private CapturingInstanceService instanceService;
    private WorklistCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        instanceService = new CapturingInstanceService();
        service = new WorklistCommandServiceImpl(instanceService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimWorkItemsReturnsItemLevelResults() throws Exception {
        Map<String, Object> roleMapping = new LinkedHashMap<>();
        roleMapping.put("endpoint", "hong");
        roleMapping.put("resourceName", "홍길동");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskIds", Arrays.asList("101", "102"));
        body.put("roleMapping", roleMapping);

        instanceService.failTaskId = "102";

        Map<String, Object> result = service.claimWorkItems(body);

        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failureCount"));

        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertEquals(true, results.get(0).get("success"));
        assertEquals(false, results.get(1).get("success"));
        assertEquals("No permission", results.get(1).get("reason"));

        assertEquals("101", instanceService.calls.get(0).taskId);
        assertEquals("hong", instanceService.calls.get(0).roleMapping.getEndpoint());
        assertEquals("홍길동", instanceService.calls.get(0).roleMapping.getResourceName());
    }

    @Test
    void claimWorkItemsRejectsMissingTaskIds() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.claimWorkItems(new LinkedHashMap<>()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void unclaimWorkItemsUsesEmptyRoleMapping() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskIds", Arrays.asList("201"));
        body.put("mode", "unclaim");

        service.claimWorkItems(body);

        assertEquals("201", instanceService.calls.get(0).taskId);
        assertEquals(null, instanceService.calls.get(0).roleMapping.getEndpoint());
    }

    private static class CapturingInstanceService extends InstanceServiceImpl {
        private final List<Call> calls = new ArrayList<>();
        private String failTaskId;

        @Override
        public void claimWorkItem(String taskId, RoleMappingCommand roleMapping) {
            calls.add(new Call(taskId, roleMapping));
            if (taskId.equals(failTaskId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission");
            }
        }
    }

    private static class Call {
        private final String taskId;
        private final RoleMappingCommand roleMapping;

        private Call(String taskId, RoleMappingCommand roleMapping) {
            this.taskId = taskId;
            this.roleMapping = roleMapping;
        }
    }
}
