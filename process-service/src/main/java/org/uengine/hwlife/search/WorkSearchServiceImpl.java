package org.uengine.hwlife.search;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.search.dto.*;

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
  public OrgRunningResponse searchOrgRunning(@RequestBody OrgRunningRequest request) {
    throw notImplemented("searchOrgRunning");
  }

  @Override
  @Transactional(readOnly = true)
  public OrgCompletedResponse searchOrgCompleted(@RequestBody OrgCompletedRequest request) {
    throw notImplemented("searchOrgCompleted");
  }

  @Override
  @Transactional(readOnly = true)
  public BulkAssignSearchResponse searchBulkAssign(@RequestBody BulkAssignSearchRequest request) {
    throw notImplemented("searchBulkAssign");
  }

  @Override
  @Transactional(readOnly = true)
  public RunningWorkByCorrKeyResponse searchWorklistByInstId(@RequestBody RunningWorkByCorrKeyRequest request) {
    throw notImplemented("searchWorklistByInstId");
  }

  @Override
  @Transactional(readOnly = true)
  public RunningTasksByKeyResponse searchRunningWorkByCorrKey(@RequestBody RunningTasksByKeyRequest request) {
    RunningTasksByKeyResponse response = new RunningTasksByKeyResponse();

    RunningTasksByKeyResponseItem item = new RunningTasksByKeyResponseItem();
    item.setLoanPcesMgmtNo("LOAN-2026-0001");
    item.setFncgBpmTaskTrcgNm("FN013_S03_402");
    item.setFncgBpmUworSttsCntn("NEW");
    item.setPrgsSttsNm("RUNNING");
    item.setPrcsrsltCntn("정상(인스턴스: RUNNING, 단위업무상태: NEW)");
  
    response.setBswrList(Arrays.asList(item));
    return response;
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
