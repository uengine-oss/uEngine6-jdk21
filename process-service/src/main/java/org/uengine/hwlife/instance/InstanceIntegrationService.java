package org.uengine.hwlife.instance;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.hwlife.instance.dto.*;

/**
 * 인스턴스 단위 커스텀 연동 REST API — 외부·hwlife 전용.
 *
 * <p>코어 {@link org.uengine.five.service.InstanceService} 위에 담당자 관리, 흐름 제어, 상태 동기화를 제공한다.
 * 구현: {@link InstanceIntegrationServiceImpl}.</p>
 *
 * <ul>
 *   <li>담당자 관리: 선점, 위임, 재배정</li>
 *   <li>흐름 관리: SKIP, 반송, 점프</li>
 *   <li>동기화: 업무 상태 동기화</li>
 * </ul>
 */
@RequestMapping("/instance")
public interface InstanceIntegrationService {

  /**
   * 다중 선점 / 선점 해제 (모든 사용자).
   *
   * <p>처리자 사번·소속기관은 ESB header.emnb / header.belnOrgnCode 사용.
   * 선점 대상은 {@code dispatchOption == 1 AND endpoint IS NULL AND groupCd == belnOrgnCode}.</p>
   *
   * <p>ESB header {@code prcsRsltDvsnCode=0}(성공). 건별 실패 사유는
   * {@code failList[].prcsRsltCntn}({@code LBM05XXXX}).
   * 코드 목록은 {@link InstanceIntegrationServiceImpl#claimWorkItems} 주석 참고.</p>
   *
   * <pre>POST /instance/multi-claim</pre>
   */
  @RequestMapping(value = "/multi-claim", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  ClaimResponse claimWorkItems(@RequestBody ClaimRequest request) throws Exception;

  /**
   * 다중 업무 위임 — 본인 담당 업무만 (모든 사용자).
   *
   * <p>위임자는 ESB header.emnb, 처리자는 body {@code hndrEmnb}(기관은 IAM 조회).
   * 단일 위임은 {@code POST /work-item/{taskId}/delegate}.
   * 건별 실패 사유는 {@code failList[].prcsRsltCntn}({@code LBM04XXXX}),
   * 성공은 {@code LBM000000}. 코드 목록은
   * {@link InstanceIntegrationServiceImpl#delegateWorkItems} 주석 참고.</p>
   *
   * <pre>POST /instance/multi-delegate</pre>
   */
  @RequestMapping(value = "/multi-delegate", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request) throws Exception;

  /**
   * 일괄 배정 — 여러 선점 대상 업무를 업무별 지정 담당자에게 한 번에 배정 (권한자).
   *
   * <p>조회는 {@link org.uengine.hwlife.search.WorkSearchService#searchBulkAssign}. 본인 선점은 {@link #claimWorkItems}.</p>
   *
   * <pre>PUT /instance/bulk-assign</pre>
   */
  @RequestMapping(value = "/bulk-assign", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  BulkAssignResponse assignBulk(@RequestBody BulkAssignRequest request) throws Exception;

  /**
   * 다중 업무 담당자 변경 — 권한자가 업무별 처리자({@code hndrEmnb})·기관({@code hndrOrgnCode})을 지정.
   *
   * <p>요청자 사번은 ESB header.emnb. 본인 업무 조건 없음.</p>
   *
   * <pre>POST /instance/multi-reassign</pre>
   */
  @RequestMapping(value = "/multi-reassign", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  ReassignResponse reassignWorkItems(@RequestBody ReassignRequest request) throws Exception;

  /**
   * 단위업무 SKIP — 엔진 {@code POST /work-item/{taskId}/skip} 위임.
   *
   * <pre>POST /instance/skip</pre>
   */
  @RequestMapping(value = "/skip", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  TaskSkipResponse skipWorklist(@RequestBody TaskSkipRequest request) throws Exception;

  /**
   * 단위업무 반송(이전 단계) — 엔진 {@code POST /work-item/{taskId}/return} 위임.
   *
   * <pre>POST /instance/return</pre>
   */
  @RequestMapping(value = "/return", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  TaskReturnResponse returnToPrevious(@RequestBody TaskReturnRequest request) throws Exception;

  /**
   * 단위업무 점프(강제 이동) — 엔진 {@code POST /instance/{instanceId}/activity/{tracingTag}/backToHere} 위임.
   *
   * <pre>POST /instance/jump</pre>
   */
  @RequestMapping(value = "/jump", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  TaskJumpResponse jumpToForward(@RequestBody TaskJumpRequest request) throws Exception;

  /**
   * 업무 상태 동기화 — 외부 시스템 기준 인스턴스·워크리스트 상태 반영.
   *
   * <pre>POST /instance/sync</pre>
   */
  @RequestMapping(value = "/sync", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
  InstanceSyncResponse syncInstances(@RequestBody InstanceSyncRequest request) throws Exception;
}
