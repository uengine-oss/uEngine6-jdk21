package org.uengine.hwlife.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.*;

/**
 * 한화생명 융자차세대 — BPM 통합 검색 REST API.
 *
 * <p>구현: {@link WorkSearchServiceImpl}. 필터·응답 DTO는 추후 확장.</p>
 *
 * <pre>
 *   POST /search/my-todo  body: { "custNo": "...", "pageNo": "1" }
 *   POST /search/my-progress  body: { "custNo": "...", "pageNo": "1" }
 *   GET /search/org-running?page=0&amp;size=20
 *   GET /search/org-completed?page=0&amp;size=20
 *   GET /search/bulk-assign?page=0&amp;size=20
 *   POST /search/running-by-key  body: { "corrKey": "..." }
 * </pre>
 */
@RequestMapping("/search")
public interface WorkSearchService {

  @RequestMapping(value = "/my-todo", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) throws Exception;

  @RequestMapping(value = "/my-progress", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  MyProgressResponse searchMyProgress(@RequestBody MyProgressRequest request) throws Exception;

  @RequestMapping(value = "/org-running", method = RequestMethod.GET)
  Page<ProcessInstanceEntity> searchOrgRunning(Pageable pageable) throws Exception;

  @RequestMapping(value = "/org-completed", method = RequestMethod.GET)
  Page<ProcessInstanceEntity> searchOrgCompleted(Pageable pageable) throws Exception;

  @RequestMapping(value = "/bulk-assign", method = RequestMethod.GET)
  Page<WorklistEntity> searchBulkAssign(Pageable pageable) throws Exception;

  // @RequestMapping(value = "/worklist-by-inst-id", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  // Page<WorklistEntity> searchWorklistByInstId(Pageable pageable) throws Exception;

  @RequestMapping(value = "/running-by-key", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8")
  RunningTasksByKeyResponse searchRunningWorkByCorrKey(@RequestBody RunningTasksByKeyRequest request) throws Exception;
}
