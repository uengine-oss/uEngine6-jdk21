package org.uengine.hwlife.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;

/**
 * 한화생명 융자차세대 — BPM 통합 검색 REST API.
 *
 * <p>구현: {@link BpmSearchServiceImpl}. 필터·응답 DTO는 추후 확장.</p>
 *
 * <pre>
 *   GET /search/my-todo?page=0&amp;size=20
 *   GET /search/my-progress?page=0&amp;size=20
 *   GET /search/org-running?page=0&amp;size=20
 *   GET /search/org-completed?page=0&amp;size=20
 *   GET /search/org-batch?page=0&amp;size=20
 * </pre>
 */
@RequestMapping("/search")
public interface BpmSearchService {

  @RequestMapping(value = "/my-todo", method = RequestMethod.GET)
  Page<WorklistEntity> searchMyTodo(Pageable pageable) throws Exception;

  @RequestMapping(value = "/my-progress", method = RequestMethod.GET)
  Page<ProcessInstanceEntity> searchMyProgress(Pageable pageable) throws Exception;

  @RequestMapping(value = "/org-running", method = RequestMethod.GET)
  Page<ProcessInstanceEntity> searchOrgRunning(Pageable pageable) throws Exception;

  @RequestMapping(value = "/org-completed", method = RequestMethod.GET)
  Page<ProcessInstanceEntity> searchOrgCompleted(Pageable pageable) throws Exception;

  @RequestMapping(value = "/org-batch", method = RequestMethod.GET)
  Page<WorklistEntity> searchOrgBatch(Pageable pageable) throws Exception;
}
