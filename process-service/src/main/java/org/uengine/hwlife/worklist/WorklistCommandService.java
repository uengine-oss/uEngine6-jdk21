package org.uengine.hwlife.worklist;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.uengine.five.dto.BulkDelegateWorkItemCommand;
import org.uengine.five.dto.BulkDelegateWorkItemResult;

/**
 * 워크리스트 일괄 처리 REST API.
 *
 * <p>구현: {@link WorklistCommandServiceImpl}. 전용 DTO는 추후 확장.</p>
 */
@RequestMapping("/worklist")
public interface WorklistCommandService {

  /**
   * 다중 선점 / 선점 해제 (모든 사용자).
   *
   * <pre>POST /worklist/claim</pre>
   */
  @RequestMapping(value = "/claim", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  Map<String, Object> claimWorkItems(@RequestBody(required = false) Map<String, Object> body) throws Exception;

  /**
   * 다중 업무 위임 — 본인 담당 업무만 (모든 사용자).
   *
   * <pre>POST /worklist/delegate</pre>
   */
  @RequestMapping(value = "/delegate", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  BulkDelegateWorkItemResult delegateWorkItems(@RequestBody BulkDelegateWorkItemCommand command) throws Exception;

  /**
   * 일괄배정 업무 담당자 설정 (권한자).
   *
   * <pre>PUT /worklist/org-batch/assignee</pre>
   */
  @PutMapping("/org-batch/assignee")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void assignOrgBatchAssignee(@RequestBody(required = false) Map<String, Object> body) throws Exception;

  /**
   * 다중 업무 담당자 변경 — 본인 업무 조건 없음 (권한자).
   *
   * <pre>POST /worklist/reassign</pre>
   */
  @RequestMapping(value = "/reassign", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  Map<String, Object> reassignWorkItems(@RequestBody(required = false) Map<String, Object> body) throws Exception;
}
