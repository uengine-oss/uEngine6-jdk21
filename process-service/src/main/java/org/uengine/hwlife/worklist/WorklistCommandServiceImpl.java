package org.uengine.hwlife.worklist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.worklist.dto.BulkDelegateWorkItemCommand;
import org.uengine.hwlife.worklist.dto.BulkDelegateWorkItemResult;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.five.dto.RoleMappingCommand;

/**
 * 워크리스트 일괄 처리 REST API 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class WorklistCommandServiceImpl implements WorklistCommandService {

  private final InstanceServiceImpl instanceService;

  public WorklistCommandServiceImpl(InstanceServiceImpl instanceService) {
    this.instanceService = instanceService;
  }

  @Override
  public Map<String, Object> claimWorkItems(@RequestBody(required = false) Map<String, Object> body) throws Exception {
    List<String> taskIds = extractTaskIds(body);
    if (taskIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskIds is required");
    }

    RoleMappingCommand roleMapping = extractRoleMapping(body);
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> itemResults = new ArrayList<>();
    Set<String> processed = new LinkedHashSet<>();
    int successCount = 0;
    int failureCount = 0;

    for (String taskId : taskIds) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("taskId", taskId);

      if (taskId == null || taskId.trim().isEmpty()) {
        item.put("success", false);
        item.put("reason", "taskId is required");
        failureCount++;
        itemResults.add(item);
        continue;
      }

      String normalizedTaskId = taskId.trim();
      item.put("taskId", normalizedTaskId);
      if (!processed.add(normalizedTaskId)) {
        item.put("success", false);
        item.put("reason", "Duplicated taskId");
        failureCount++;
        itemResults.add(item);
        continue;
      }

      try {
        instanceService.claimWorkItem(normalizedTaskId, roleMapping);
        item.put("success", true);
        successCount++;
      } catch (Exception e) {
        item.put("success", false);
        item.put("reason", resolveFailureReason(e));
        failureCount++;
      }
      itemResults.add(item);
    }

    result.put("total", taskIds.size());
    result.put("successCount", successCount);
    result.put("failureCount", failureCount);
    result.put("results", itemResults);
    return result;
  }

  @Override
  @Transactional
  public BulkDelegateWorkItemResult delegateWorkItems(@RequestBody BulkDelegateWorkItemCommand command)
      throws Exception {
    return instanceService.delegateWorkItems(command);
  }

  @Override
  @Transactional
  public void assignOrgBatchAssignee(@RequestBody(required = false) Map<String, Object> body) throws Exception {
    throw notImplemented("assignOrgBatchAssignee");
  }

  @Override
  @Transactional
  public Map<String, Object> reassignWorkItems(@RequestBody(required = false) Map<String, Object> body)
      throws Exception {
    throw notImplemented("reassignWorkItems");
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }

  private static List<String> extractTaskIds(Map<String, Object> body) {
    List<String> taskIds = new ArrayList<>();
    if (body == null) {
      return taskIds;
    }

    Object rawTaskIds = body.get("taskIds");
    if (rawTaskIds instanceof Iterable<?>) {
      for (Object rawTaskId : (Iterable<?>) rawTaskIds) {
        taskIds.add(rawTaskId == null ? null : String.valueOf(rawTaskId));
      }
    } else if (rawTaskIds != null) {
      taskIds.add(String.valueOf(rawTaskIds));
    }

    Object rawTaskId = body.get("taskId");
    if (rawTaskIds == null && rawTaskId != null) {
      taskIds.add(String.valueOf(rawTaskId));
    }

    return taskIds;
  }

  @SuppressWarnings("unchecked")
  private static RoleMappingCommand extractRoleMapping(Map<String, Object> body) {
    RoleMappingCommand command = new RoleMappingCommand();
    if (body == null) {
      return command;
    }

    Object rawUnclaim = body.get("unclaim");
    Object rawMode = body.get("mode");
    boolean unclaim = Boolean.TRUE.equals(rawUnclaim)
        || (rawMode != null && "unclaim".equalsIgnoreCase(String.valueOf(rawMode)));
    if (unclaim) {
      return command;
    }

    Object rawRoleMapping = body.get("roleMapping");
    if (!(rawRoleMapping instanceof Map<?, ?>)) {
      rawRoleMapping = body.get("claimRoleMapping");
    }
    Map<String, Object> roleMapping = rawRoleMapping instanceof Map<?, ?>
        ? (Map<String, Object>) rawRoleMapping
        : body;

    command.setEndpoint(firstString(roleMapping, "endpoint", "userId", "assigneeEndpoint"));
    command.setResourceName(firstString(roleMapping, "resourceName", "userName", "assigneeName"));
    command.setAssignGroup(firstString(roleMapping, "assignGroup", "groupCd", "groupCode"));
    command.setScope(firstString(roleMapping, "scope", "roleCode"));
    command.setTargetType(firstString(roleMapping, "targetType"));
    return command;
  }

  private static String firstString(Map<String, Object> values, String... keys) {
    if (values == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      Object value = values.get(key);
      if (value != null && !String.valueOf(value).trim().isEmpty()) {
        return String.valueOf(value).trim();
      }
    }
    return null;
  }

  private static String resolveFailureReason(Exception e) {
    Throwable cursor = e;
    while (cursor != null) {
      if (cursor instanceof ResponseStatusException) {
        ResponseStatusException responseStatusException = (ResponseStatusException) cursor;
        String reason = responseStatusException.getReason();
        return reason != null ? reason : responseStatusException.getMessage();
      }
      cursor = cursor.getCause();
    }
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }
}
