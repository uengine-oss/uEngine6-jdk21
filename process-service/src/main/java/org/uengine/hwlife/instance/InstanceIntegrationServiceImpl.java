package org.uengine.hwlife.instance;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.*;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.instance.dto.*;

/**
 * {@link InstanceIntegrationService} REST 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class InstanceIntegrationServiceImpl implements InstanceIntegrationService {

  private final InstanceServiceImpl instanceService;
  private final WorklistRepository worklistRepository;

  public InstanceIntegrationServiceImpl(
      InstanceServiceImpl instanceService,
      WorklistRepository worklistRepository) {
    this.instanceService = instanceService;
    this.worklistRepository = worklistRepository;
  }

  @Override
  @Transactional
  public ClaimResponse claimWorkItems(@RequestBody ClaimRequest request) throws Exception {
    throw notImplemented("claimWorkItems");
  }

  @Override
  @Transactional
  public DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request)
      throws Exception {
    throw notImplemented("delegateWorkItems");
  }

  @Override
  @Transactional
  public BulkAssignResponse assignBulk(@RequestBody BulkAssignRequest request) throws Exception {
    throw notImplemented("assignBulk");
  }

  @Override
  @Transactional
  public ReassignResponse reassignWorkItems(@RequestBody ReassignRequest request)
      throws Exception {
    throw notImplemented("reassignWorkItems");
  }

  @Override
  @Transactional
  public TaskSkipResponse skipWorklist(@RequestBody TaskSkipRequest request) throws Exception {
    throw notImplemented("skipWorklist");
  }

  @Override
  @Transactional
  public TaskReturnResponse returnToPrevious(@RequestBody TaskReturnRequest request) throws Exception {
    throw notImplemented("returnToPrevious");
  }

  @Override
  @Transactional(rollbackFor = { Exception.class })
  public TaskJumpResponse jumpToForward(@RequestBody TaskJumpRequest request) throws Exception {
    WorklistEntity worklist = worklistRepository.findById(Long.parseLong(request.getTaskId())).orElse(null);
    if (worklist == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "No such work item where taskId = " + request.getTaskId());
    }
    InstanceResource instance = instanceService.backToHere(
        String.valueOf(worklist.getInstId()), request.getTargetTracingTag());
    return TaskJumpResponse.from(instance, request);
  }

  @Override
  @Transactional
  public InstanceSyncResponse syncInstances(@RequestBody InstanceSyncRequest request) throws Exception {
    return new InstanceSyncResponse();
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
