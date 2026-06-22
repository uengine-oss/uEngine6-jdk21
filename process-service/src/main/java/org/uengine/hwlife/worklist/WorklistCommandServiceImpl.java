package org.uengine.hwlife.worklist;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.BulkDelegateWorkItemCommand;
import org.uengine.five.dto.BulkDelegateWorkItemResult;
import org.uengine.five.service.InstanceServiceImpl;

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
  @Transactional
  public Map<String, Object> claimWorkItems(@RequestBody(required = false) Map<String, Object> body) throws Exception {
    throw notImplemented("claimWorkItems");
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
}
