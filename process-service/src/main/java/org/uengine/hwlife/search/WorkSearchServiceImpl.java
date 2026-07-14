package org.uengine.hwlife.search;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.search.dto.BulkAssignSearchRequest;
import org.uengine.hwlife.search.dto.BulkAssignSearchResponse;
import org.uengine.hwlife.search.dto.MyProgressRequest;
import org.uengine.hwlife.search.dto.MyProgressResponse;
import org.uengine.hwlife.search.dto.MyTodoRequest;
import org.uengine.hwlife.search.dto.MyTodoResponse;
import org.uengine.hwlife.search.dto.OrgCompletedRequest;
import org.uengine.hwlife.search.dto.OrgCompletedResponse;
import org.uengine.hwlife.search.dto.OrgRunningRequest;
import org.uengine.hwlife.search.dto.OrgRunningResponse;
import org.uengine.hwlife.search.dto.RunningTasksByKeyRequest;
import org.uengine.hwlife.search.dto.RunningTasksByKeyResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequest;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponse;

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
    response.setLoanPcesMgmtNo("LOAN-2026-0001");
    response.setFncgBpmTaskTrcgNm("FN013_S03_402");
    response.setFncgBpmUworSttsCntn("NEW");
    response.setPrgsSttsNm("RUNNING");
    response.setPrcsrsltCntn("정상(인스턴스: RUNNING, 단위업무상태: NEW)");
    return response;
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
