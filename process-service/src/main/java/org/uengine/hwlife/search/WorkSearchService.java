package org.uengine.hwlife.search;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.hwlife.search.dto.*;

/**
 * 한화생명 융자차세대 — BPM 통합 검색 REST API.
 *
 * <p>구현: {@link WorkSearchServiceImpl}. 필터·응답 DTO는 추후 확장.</p>
 *
 * <pre>
 *   POST /search/my-todo  body: { "custId": "...", "pageNo": "1" }
 *   POST /search/my-progress  body: { "custId": "...", "pageNo": "1" }
 *   POST /search/org-running  body: { "custId": "...", "pageNo": "1" }
 *   POST /search/org-completed  body: { "custId": "...", "pageNo": "1" }
 *   POST /search/bulk-assign  body: { "custId": "...", "hndrEmnb": "..." }
 *   POST /search/worklist-by-inst-id  body: { "loanPcesMgmtNo": "..." }
 *   POST /search/running-by-key  body: { "loanPcesMgmtNo": "..." }
 * </pre>
 */
@RequestMapping("/search")
public interface WorkSearchService {

  @RequestMapping(value = "/my-todo", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) throws Exception;

  @RequestMapping(value = "/my-progress", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  MyProgressResponse searchMyProgress(@RequestBody MyProgressRequest request) throws Exception;

  @RequestMapping(value = "/org-running", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  OrgRunningResponse searchOrgRunning(@RequestBody OrgRunningRequest request) throws Exception;

  @RequestMapping(value = "/org-completed", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  OrgCompletedResponse searchOrgCompleted(@RequestBody OrgCompletedRequest request) throws Exception;

  // 일괄배정(다중 강제 선점 처리)
  @RequestMapping(value = "/bulk-assign", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  BulkAssignSearchResponse searchBulkAssign(@RequestBody BulkAssignSearchRequest request) throws Exception;

  // 인스턴스 기준 단위업무 목록
  @RequestMapping(value = "/worklist-by-inst-id", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  WorklistByInstIdResponseItem searchWorklistByInstId(@RequestBody WorklistByInstIdRequest request) throws Exception;

  // 현재 진행중인 단위업무 및 인스턴스 상태
  @RequestMapping(value = "/running-by-key", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  RunningWorkByCorrKeyResponse searchRunningWorkByCorrKey(@RequestBody RunningWorkByCorrKeyRequest request) throws Exception;
}
