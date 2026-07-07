package org.uengine.hwlife.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.MyProgressRequest;
import org.uengine.hwlife.search.dto.MyProgressResponse;
import org.uengine.hwlife.search.dto.MyTodoRequest;
import org.uengine.hwlife.search.dto.MyTodoResponse;
import org.uengine.hwlife.search.dto.RunningTasksByKeyRequest;
import org.uengine.hwlife.search.dto.RunningTasksByKeyResponse;

/**
 * BPM 통합 검색 REST API 구현. Repository 연동은 추후 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class WorkSearchServiceImpl implements WorkSearchService {

  @Override
  @Transactional(readOnly = true)
  public MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) {
    throw notImplemented("searchMyTodo");
  }

  @Override
  @Transactional(readOnly = true)
  public MyProgressResponse searchMyProgress(@RequestBody MyProgressRequest request) {
    throw notImplemented("searchMyProgress");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProcessInstanceEntity> searchOrgRunning(
      @PageableDefault(size = 20, sort = "startedDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchOrgRunning");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProcessInstanceEntity> searchOrgCompleted(
      @PageableDefault(size = 20, sort = "finishedDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchOrgCompleted");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<WorklistEntity> searchBulkAssign(
      @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchBulkAssign");
  }

  // @Override
  // @Transactional(readOnly = true)
  // public Page<WorklistEntity> searchWorklistByInstId(Object instId) {
  //   throw notImplemented("searchWorklistByInstId");
  // }

  @Override
  @Transactional(readOnly = true)
  public RunningTasksByKeyResponse searchRunningWorkByCorrKey(@RequestBody RunningTasksByKeyRequest request) {
    RunningTasksByKeyResponse response = new RunningTasksByKeyResponse();
    response.setLoanPcesMgmtNo("LOAN-2026-0001");
    response.setTrcTag("T01");
    response.setStatus("RUNNING");
    response.setRsltMsgeCntn("정상 처리중(TEST)");
    return response;
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
