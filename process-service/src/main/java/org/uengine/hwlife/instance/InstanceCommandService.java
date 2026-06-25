package org.uengine.hwlife.instance;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.uengine.hwlife.instance.dto.ClaimRequest;
import org.uengine.hwlife.instance.dto.ClaimResponse;
import org.uengine.hwlife.instance.dto.DelegateRequest;
import org.uengine.hwlife.instance.dto.DelegateResponse;

/**
 * 인스턴스 명령 REST API — {@link org.uengine.five.service.InstanceService} 커스텀 영역.
 *
 * <p>구현: {@link InstanceCommandServiceImpl}.</p>
 */
@RequestMapping("/instance")
public interface InstanceCommandService {

  /**
   * 다중 선점 / 선점 해제 (모든 사용자).
   *
   * <pre>POST /instance/multi-claim</pre>
   */
  @RequestMapping(value = "/multi-claim", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  ClaimResponse claimWorkItems(@RequestBody ClaimRequest request) throws Exception;

  /**
   * 다중 업무 위임 — 본인 담당 업무만 (모든 사용자).
   *
   * <p>단일 위임은 {@code POST /work-item/{taskId}/delegate}.</p>
   *
   * <pre>POST /instance/multi-delegate</pre>
   */
  @RequestMapping(value = "/multi-delegate", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request) throws Exception;

  /**
   * 일괄배정 업무 담당자 설정 (권한자).
   *
   * <pre>PUT /instance/org-batch/assignee</pre>
   */
  @PutMapping("/org-batch/assignee")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void assignOrgBatchAssignee(@RequestBody(required = false) Map<String, Object> body) throws Exception;

  /**
   * 다중 업무 담당자 변경 — 본인 업무 조건 없음 (권한자).
   *
   * <pre>POST /instance/reassign</pre>
   */
  @RequestMapping(value = "/reassign", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  Map<String, Object> reassignWorkItems(@RequestBody(required = false) Map<String, Object> body) throws Exception;
}
