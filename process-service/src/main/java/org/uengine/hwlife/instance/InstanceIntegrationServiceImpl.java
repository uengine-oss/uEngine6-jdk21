package org.uengine.hwlife.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.*;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.five.spring.SecurityAwareServletFilter;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.RoleMapping;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;
import org.uengine.hwlife.iam.ExternalIAMService;
import org.uengine.hwlife.iam.dto.FncgOrgInfo;
import org.uengine.hwlife.iam.dto.UserSearchResponse;
import org.uengine.hwlife.instance.dto.*;

/**
 * {@link InstanceIntegrationService} REST 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class InstanceIntegrationServiceImpl implements InstanceIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(InstanceIntegrationServiceImpl.class);

  private final InstanceServiceImpl instanceService;
  private final WorklistRepository worklistRepository;
  @SuppressWarnings("unused") // isReassignAuthorized ESB 연동 시 사용
  private final EsbClient esbClient;
  private final BulkAssignItemService bulkAssignItemService;

  public InstanceIntegrationServiceImpl(
      InstanceServiceImpl instanceService,
      WorklistRepository worklistRepository,
      EsbClient esbClient,
      BulkAssignItemService bulkAssignItemService) {
    this.instanceService = instanceService;
    this.worklistRepository = worklistRepository;
    this.esbClient = esbClient;
    this.bulkAssignItemService = bulkAssignItemService;
  }

  /**
   * 다중 선점 / 선점 해제.
   *
   * <p>처리결과 코드는 {@code failList[].prcsRsltCntn}({@code LBM05XXXX}).
   * ESB header {@code prcsRsltDvsnCode} 는 성공 {@code 0} / 시스템실패 {@code 1}.
   * <ul>
   *   <li>{@code LBM050001} — request body 없음</li>
   *   <li>{@code LBM050002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM050003} — header.emnb 없음</li>
   *   <li>{@code LBM050004} — dvsnVal 이 0(선점)/1(해제) 이 아님</li>
   *   <li>{@code LBM050005} — 선점 시 header.belnOrgnCode 없음</li>
   *   <li>{@code LBM050006} — fncgBpmTaskLstId 없음</li>
   *   <li>{@code LBM050007} — 요청 내 fncgBpmTaskLstId 중복</li>
   *   <li>{@code LBM050008} — fncgBpmTaskLstId 비숫자</li>
   *   <li>{@code LBM050009} — work item 없음</li>
   *   <li>{@code LBM050010} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM050011} — 선점불가(status != NEW)</li>
   *   <li>{@code LBM050012} — 이미 본인이 선점한 업무</li>
   *   <li>{@code LBM050013} — 이미 다른 담당자가 선점한 업무</li>
   *   <li>{@code LBM050014} — 선점규칙 업무가 아님(dispatchOption != 1)</li>
   *   <li>{@code LBM050015} — 본인 기관이 아닌 업무(groupCd != belnOrgnCode)</li>
   *   <li>{@code LBM050016} — 선점 해제불가(status != NEW)</li>
   *   <li>{@code LBM050017} — 이미 선점 해제된 업무</li>
   *   <li>{@code LBM050018} — 본인 선점 건이 아님(타인 선점)</li>
   *   <li>{@code LBM050019} — claimWorkItem 업무 예외</li>
   *   <li>{@code LBM050020} — 기타 예외</li>
   * </ul>
   *
   * <p>건별 성공/실패를 독립 처리하므로 바깥 {@code @Transactional} 을 두지 않는다
   * (내부 {@code claimWorkItem} 예외 시 rollback-only → UnexpectedRollbackException 방지).</p>
   */
  @Override
  public ClaimResponse claimWorkItems(@RequestBody ClaimRequest request) throws Exception {
    List<ClaimRequestItem> bswrList = request == null ? null : request.getBswrList();

    EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
    String actorEndpoint = trimToNull(header != null ? header.getEmnb() : null);
    String belnOrgnCode = trimToNull(header != null ? header.getBelnOrgnCode() : null);

    String commonError = resolveCommonClaimError(request, bswrList, actorEndpoint, belnOrgnCode);
    if (commonError != null) {
      return failedClaimResponse(bswrList, 0, commonError);
    }

    boolean unclaim = "1".equals(trimToNull(request.getDvsnVal()));
    UserContext.getThreadLocalInstance().setUserId(actorEndpoint);

    List<ClaimResponseItem> failList = new ArrayList<>();
    Set<String> seenTaskIds = new HashSet<>();
    int successCount = 0;

    for (ClaimRequestItem item : bswrList) {
      String taskId = item == null ? null : trimToNull(item.getFncgBpmTaskLstId());
      if (taskId == null) {
        addClaimFailure(failList, item, "LBM050006");
        continue;
      }
      if (!seenTaskIds.add(taskId)) {
        addClaimFailure(failList, item, "LBM050007");
        continue;
      }

      try {
        WorklistEntity worklist = worklistRepository.findById(Long.parseLong(taskId)).orElse(null);
        if (worklist == null) {
          addClaimFailure(failList, item, "LBM050009");
          continue;
        }
        String validationError = validateClaimRequest(worklist, item, actorEndpoint, belnOrgnCode, unclaim);
        if (validationError != null) {
          addClaimFailure(failList, item, validationError);
          continue;
        }

        RoleMappingCommand roleMapping = null;
        if (!unclaim) {
          roleMapping = new RoleMappingCommand();
          roleMapping.setEndpoint(actorEndpoint);
        }
        instanceService.claimWorkItem(taskId, roleMapping);
        successCount++;
      } catch (NumberFormatException e) {
        addClaimFailure(failList, item, "LBM050008");
      } catch (ResponseStatusException e) {
        addClaimFailure(failList, item, "LBM050019");
      } catch (Exception e) {
        addClaimFailure(failList, item, "LBM050020");
      }
    }

    return toClaimResponse(successCount, failList);
  }

  /**
   * 요청 전체 공통 실패 코드.
   *
   * <ul>
   *   <li>{@code LBM050001} — request body 없음</li>
   *   <li>{@code LBM050002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM050003} — header.emnb 없음</li>
   *   <li>{@code LBM050004} — dvsnVal 이 0/1 이 아님</li>
   *   <li>{@code LBM050005} — 선점 시 header.belnOrgnCode 없음</li>
   * </ul>
   */
  private static String resolveCommonClaimError(
      ClaimRequest request,
      List<ClaimRequestItem> bswrList,
      String actorEndpoint,
      String belnOrgnCode) {
    if (request == null) {
      return "LBM050001";
    }
    if (bswrList == null || bswrList.isEmpty()) {
      return "LBM050002";
    }
    if (actorEndpoint == null) {
      return "LBM050003";
    }
    String dvsnVal = trimToNull(request.getDvsnVal());
    if (!"0".equals(dvsnVal) && !"1".equals(dvsnVal)) {
      return "LBM050004";
    }
    if ("0".equals(dvsnVal) && belnOrgnCode == null) {
      return "LBM050005";
    }
    return null;
  }

  /** 공통 사유 코드로 요청 태스크 전부를 실패 처리한다. */
  private static ClaimResponse failedClaimResponse(
      List<ClaimRequestItem> bswrList,
      int successCount,
      String sharedReason) {
    List<ClaimResponseItem> failList = new ArrayList<>();
    if (bswrList != null) {
      for (ClaimRequestItem item : bswrList) {
        addClaimFailure(failList, item, sharedReason);
      }
    }
    if (failList.isEmpty()) {
      addClaimFailure(failList, null, sharedReason);
    }
    return toClaimResponse(successCount, failList);
  }

  private static ClaimResponse toClaimResponse(
      int successCount,
      List<ClaimResponseItem> failList) {
    ClaimResponse response = new ClaimResponse();
    response.setSucsCont(successCount);
    response.setFailCont(failList == null ? 0 : failList.size());
    response.setFailList(failList == null ? new ArrayList<>() : failList);
    return response;
  }

  /**
   * 단건 선점/선점 해제 검증. 실패 시 LBM 코드, 통과 시 {@code null}.
   *
   * <ul>
   *   <li>{@code LBM050010} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM050011} — 선점불가(status != NEW)</li>
   *   <li>{@code LBM050012} — 이미 본인이 선점한 업무</li>
   *   <li>{@code LBM050013} — 이미 다른 담당자가 선점한 업무</li>
   *   <li>{@code LBM050014} — 선점규칙 업무가 아님(dispatchOption != 1)</li>
   *   <li>{@code LBM050015} — 본인 기관이 아닌 업무</li>
   *   <li>{@code LBM050016} — 선점 해제불가(status != NEW)</li>
   *   <li>{@code LBM050017} — 이미 선점 해제된 업무</li>
   *   <li>{@code LBM050018} — 본인 선점 건이 아님</li>
   * </ul>
   */
  private String validateClaimRequest(
      WorklistEntity worklist,
      ClaimRequestItem requestItem,
      String actorEndpoint,
      String belnOrgnCode,
      boolean unclaim) {
    String requestedInstanceId = requestItem == null ? null : trimToNull(requestItem.getFncgBpmPcesIntcId());
    if (requestedInstanceId != null
        && !requestedInstanceId.equals(String.valueOf(worklist.getInstId()))
        && !requestedInstanceId.equals(String.valueOf(worklist.getRootInstId()))) {
      return "LBM050010";
    }

    if (!"NEW".equals(trimToNull(worklist.getStatus()))) {
      return unclaim ? "LBM050016" : "LBM050011";
    }

    if (worklist.getDispatchOption() != 1) {
      return "LBM050014";
    }
    if (!isSameOrganization(worklist, belnOrgnCode)) {
      return "LBM050015";
    }
    
    String currentEndpoint = trimToNull(worklist.getEndpoint());
    if (unclaim) {
      if (currentEndpoint == null) {
        return "LBM050017";
      }
      if (!currentEndpoint.equals(actorEndpoint)) {
        return "LBM050018";
      }
      return null;
    }

    if (currentEndpoint != null && currentEndpoint.equals(actorEndpoint)) {
      return "LBM050012";
    }
    if (currentEndpoint != null) {
      return "LBM050013";
    }
    return null;
  }

  /**
   * 기관 일치: {@code worklist.groupCd == organizationCode} (선점: header.belnOrgnCode).
   */
  private static boolean isSameOrganization(WorklistEntity worklist, String organizationCode) {
    String groupCd = trimToNull(worklist.getGroupCd());
    String organization = trimToNull(organizationCode);
    return groupCd != null && organization != null && groupCd.equals(organization);
  }

  private static void addClaimFailure(
      List<ClaimResponseItem> failList,
      ClaimRequestItem source,
      String reason) {
    ClaimResponseItem failure = new ClaimResponseItem();
    if (source != null) {
      failure.setFncgBpmTaskLstId(source.getFncgBpmTaskLstId());
      failure.setFncgBpmPcesIntcId(source.getFncgBpmPcesIntcId());
    }
    failure.setPrcsRsltCntn(reason);
    failList.add(failure);
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * 다중 업무 위임 — 위임자(header.emnb) 본인 담당 업무를 처리자({@code hndrEmnb})에게 이관.
   *
   * <p>처리결과 코드는 {@code failList[].prcsRsltCntn}({@code LBM04XXXX}).
   * 전체/건별 성공은 {@code LBM000000}.
   * ESB header {@code prcsRsltDvsnCode} 는 성공 {@code 0} / 시스템실패 {@code 1}.
   * <ul>
   *   <li>{@code LBM040001} — request body 없음</li>
   *   <li>{@code LBM040002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM040003} — header.emnb 없음</li>
   *   <li>{@code LBM040004} — hndrEmnb 없음</li>
   *   <li>{@code LBM040005} — 위임자(header.emnb)와 처리자가 동일</li>
   *   <li>{@code LBM040006} — 처리자 IAM 조회 실패/기관정보 없음</li>
   *   <li>{@code LBM040007} — fncgBpmTasklstId 없음</li>
   *   <li>{@code LBM040008} — 요청 내 fncgBpmTasklstId 중복</li>
   *   <li>{@code LBM040009} — fncgBpmTasklstId 비숫자</li>
   *   <li>{@code LBM040010} — work item 없음</li>
   *   <li>{@code LBM040011} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM040012} — 진행중 아님(status not in NEW,RUNNING)</li>
   *   <li>{@code LBM040013} — 선점규칙 업무 미선점(dispatchOption==1 and endpoint 없음)</li>
   *   <li>{@code LBM040014} — 본인 업무 아님(endpoint != header.emnb)</li>
   *   <li>{@code LBM040015} — 처리자 기관코드와 업무 기관코드 불일치(groupCd) — 현재 비활성</li>
   *   <li>{@code LBM040019} — delegateWorkItem 업무 예외</li>
   *   <li>{@code LBM040020} — 기타 예외</li>
   * </ul>
   *
   * <p>엔진 위임은 완전 이관({@code delegateOnlyForWorkitem=false}).
   * 검증을 모두 끝낸 뒤 건별 호출하며, 엔진 sibling sync 로 이미
   * {@code endpoint=hndrEmnb} 인 건은 재호출 없이 성공으로 집계한다.</p>
   *
   * <p>건별 성공/실패를 독립 처리하므로 바깥 {@code @Transactional} 을 두지 않는다
   * (내부 {@code delegateWorkItem} 예외 시 rollback-only → UnexpectedRollbackException 방지).</p>
   */
  @Override
  public DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request)
      throws Exception {
    List<DelegateRequestItem> bswrList = request == null ? null : request.getBswrList();
    String hndrEmnb = request == null ? null : trimToNull(request.getHndrEmnb());

    EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
    String actorEndpoint = trimToNull(header != null ? header.getEmnb() : null);

    String commonError = resolveCommonDelegateError(request, bswrList, actorEndpoint, hndrEmnb);
    if (commonError != null) {
      return failedDelegateResponse(bswrList, 0, commonError);
    }

    UserSearchResponse handler;
    try {
      handler = ExternalIAMService.getDefault().getUser(hndrEmnb);
    } catch (Exception e) {
      return failedDelegateResponse(bswrList, 0, "LBM040006");
    }
    if (!hasHandlerOrganization(handler)) {
      return failedDelegateResponse(bswrList, 0, "LBM040006");
    }

    String previousFilterUserId = SecurityAwareServletFilter.getUserId();
    UserContext.getThreadLocalInstance().setUserId(actorEndpoint);
    GlobalContext.setUserId(actorEndpoint);
    SecurityAwareServletFilter.setUserId(actorEndpoint);

    List<DelegateResponseItem> failList = new ArrayList<>();
    Set<String> seenTaskIds = new HashSet<>();
    // 완전 이관(false): sibling sync 는 엔진(InstanceServiceImpl)에 맡긴다.
    // 검증은 위임 전에 모두 끝내고, 호출 직전에 endpoint 만 재조회한다.
    List<String> validatedTaskIds = new ArrayList<>();

    try {
      for (DelegateRequestItem item : bswrList) {
        String taskId = item == null ? null : trimToNull(item.getFncgBpmTaskLstId());
        if (taskId == null) {
          addDelegateFailure(failList, item, "LBM040007");
          continue;
        }
        if (!seenTaskIds.add(taskId)) {
          addDelegateFailure(failList, item, "LBM040008");
          continue;
        }

        try {
          WorklistEntity worklist = worklistRepository.findById(Long.parseLong(taskId)).orElse(null);
          if (worklist == null) {
            addDelegateFailure(failList, item, "LBM040010");
            continue;
          }
          String validationError = validateDelegateRequest(worklist, item, actorEndpoint, handler);
          if (validationError != null) {
            addDelegateFailure(failList, item, validationError);
            continue;
          }
          validatedTaskIds.add(taskId);
        } catch (NumberFormatException e) {
          addDelegateFailure(failList, item, "LBM040009");
        } catch (Exception e) {
          addDelegateFailure(failList, item, "LBM040020");
        }
      }

      RoleMappingCommand delegated = new RoleMappingCommand();
      delegated.setEndpoint(hndrEmnb);

      int successCount = 0;
      for (String taskId : validatedTaskIds) {
        // 앞선 완전 이관의 엔진 sibling sync 로 이미 처리자면 재호출하지 않는다.
        WorklistEntity current = worklistRepository.findById(Long.parseLong(taskId)).orElse(null);
        if (current != null && hndrEmnb.equals(trimToNull(current.getEndpoint()))) {
          successCount++;
          continue;
        }
        try {
          instanceService.delegateWorkItem(taskId, delegated, false);
          successCount++;
        } catch (ResponseStatusException e) {
          addDelegateFailure(failList, findRequestItem(bswrList, taskId), "LBM040019");
        } catch (Exception e) {
          addDelegateFailure(failList, findRequestItem(bswrList, taskId), "LBM040020");
        }
      }

      return toDelegateResponse(successCount, failList);
    } finally {
      SecurityAwareServletFilter.setUserId(previousFilterUserId);
    }
  }

  private static DelegateRequestItem findRequestItem(List<DelegateRequestItem> bswrList, String taskId) {
    if (bswrList == null || taskId == null) {
      return null;
    }
    for (DelegateRequestItem item : bswrList) {
      if (item != null && taskId.equals(trimToNull(item.getFncgBpmTaskLstId()))) {
        return item;
      }
    }
    return null;
  }

  /**
   * 요청 전체 공통 실패 코드.
   *
   * <ul>
   *   <li>{@code LBM040001} — request body 없음</li>
   *   <li>{@code LBM040002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM040003} — header.emnb 없음</li>
   *   <li>{@code LBM040004} — hndrEmnb 없음</li>
   *   <li>{@code LBM040005} — 위임자와 처리자가 동일</li>
   * </ul>
   */
  private static String resolveCommonDelegateError(
      DelegateRequest request,
      List<DelegateRequestItem> bswrList,
      String actorEndpoint,
      String hndrEmnb) {
    if (request == null) {
      return "LBM040001";
    }
    if (bswrList == null || bswrList.isEmpty()) {
      return "LBM040002";
    }
    if (actorEndpoint == null) {
      return "LBM040003";
    }
    if (hndrEmnb == null) {
      return "LBM040004";
    }
    if (actorEndpoint.equals(hndrEmnb)) {
      return "LBM040005";
    }
    return null;
  }

  /** 공통 사유 코드로 요청 태스크 전부를 실패 처리한다. */
  private static DelegateResponse failedDelegateResponse(
      List<DelegateRequestItem> bswrList,
      int successCount,
      String sharedReason) {
    List<DelegateResponseItem> failList = new ArrayList<>();
    if (bswrList != null) {
      for (DelegateRequestItem item : bswrList) {
        addDelegateFailure(failList, item, sharedReason);
      }
    }
    if (failList.isEmpty()) {
      addDelegateFailure(failList, null, sharedReason);
    }
    return toDelegateResponse(successCount, failList);
  }

  private static DelegateResponse toDelegateResponse(
      int successCount,
      List<DelegateResponseItem> failList) {
    DelegateResponse response = new DelegateResponse();
    response.setSucsCont(successCount);
    response.setFailCont(failList == null ? 0 : failList.size());
    response.setFailList(failList == null ? new ArrayList<>() : failList);
    return response;
  }

  /**
   * 단건 위임 검증. 실패 시 LBM 코드, 통과 시 {@code null}.
   *
   * <ul>
   *   <li>{@code LBM040011} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM040012} — 진행중 아님</li>
   *   <li>{@code LBM040013} — 선점규칙 업무 미선점</li>
   *   <li>{@code LBM040014} — 본인 업무 아님</li>
   *   <li>{@code LBM040015} — 처리자 기관코드와 업무 기관코드 불일치 — 현재 비활성</li>
   * </ul>
   */
  private String validateDelegateRequest(
      WorklistEntity worklist,
      DelegateRequestItem requestItem,
      String actorEndpoint,
      UserSearchResponse handler) {
    String requestedInstanceId = requestItem == null ? null : trimToNull(requestItem.getFncgBpmPcesIntcId());
    if (requestedInstanceId != null
        && !requestedInstanceId.equals(String.valueOf(worklist.getInstId()))
        && !requestedInstanceId.equals(String.valueOf(worklist.getRootInstId()))) {
      return "LBM040011";
    }

    String status = trimToNull(worklist.getStatus());
    if (!"NEW".equals(status) && !"RUNNING".equals(status)) {
      return "LBM040012";
    }

    String currentEndpoint = trimToNull(worklist.getEndpoint());
    if (worklist.getDispatchOption() == 1 && currentEndpoint == null) {
      return "LBM040013";
    }
    if (currentEndpoint == null || !currentEndpoint.equals(actorEndpoint)) {
      return "LBM040014";
    }

    // TODO: 동일 기관에서만 위임 가능 — 우선 비활성화
    // if (!isHandlerSameOrganization(worklist, handler)) {
    //   return "LBM040015";
    // }
    return null;
  }

  /** 처리자 IAM 응답에 기관코드가 하나 이상 있는지. */
  private static boolean hasHandlerOrganization(UserSearchResponse handler) {
    if (handler == null || handler.getFncgWndwCodeList() == null || handler.getFncgWndwCodeList().isEmpty()) {
      return false;
    }
    for (FncgOrgInfo org : handler.getFncgWndwCodeList()) {
      if (org != null && trimToNull(org.getFncgWndwOrgnCode()) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * 업무 기관({@code worklist.groupCd})이 처리자({@code hndrEmnb}) IAM 보유 기관에 포함되는지.
   */
  private static boolean isHandlerSameOrganization(WorklistEntity worklist, UserSearchResponse handler) {
    String groupCd = trimToNull(worklist.getGroupCd());
    if (groupCd == null || handler == null || handler.getFncgWndwCodeList() == null) {
      return false;
    }
    for (FncgOrgInfo org : handler.getFncgWndwCodeList()) {
      if (org != null && groupCd.equals(trimToNull(org.getFncgWndwOrgnCode()))) {
        return true;
      }
    }
    return false;
  }

  private static void addDelegateFailure(
      List<DelegateResponseItem> failList,
      DelegateRequestItem source,
      String reason) {
    DelegateResponseItem failure = new DelegateResponseItem();
    if (source != null) {
      failure.setFncgBpmTaskLstId(source.getFncgBpmTaskLstId());
      failure.setFncgBpmPcesIntcId(source.getFncgBpmPcesIntcId());
    }
    failure.setPrcsRsltCntn(reason);
    failList.add(failure);
  }

  /**
   * 여러 미배정 업무를 한 요청으로 배정한다.
   *
   * <p>처리결과 코드는 {@code failList[].prcsRsltCntn}({@code LBM07XXXX}).</p>
   * <ul>
   *   <li>{@code LBM070001} — request body 없음</li>
   *   <li>{@code LBM070002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM070003} — header.emnb 없음</li>
   *   <li>{@code LBM070004} — hndrEmnb 없음</li>
   *   <li>{@code LBM070005} — 담당자 IAM 조회 실패</li>
   *   <li>{@code LBM070006} — fncgBpmTaskLstId 없음</li>
   *   <li>{@code LBM070007} — 요청 내 fncgBpmTaskLstId 중복</li>
   *   <li>{@code LBM070008} — fncgBpmTaskLstId 비숫자</li>
   *   <li>{@code LBM070009} — work item 없음</li>
   *   <li>{@code LBM070010} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM070011} — 배정불가(status != NEW)</li>
   *   <li>{@code LBM070012} — 이미 담당자가 지정된 업무</li>
   *   <li>{@code LBM070013} — 일괄배정 대상이 아님(dispatchOption != 1)</li>
   *   <li>{@code LBM070014} — 권한 없음</li>
   *   <li>{@code LBM070015} — header.belnOrgnCode 없음</li>
   *   <li>{@code LBM070016} — 본인 기관이 아닌 업무(groupCd != belnOrgnCode)</li>
   *   <li>{@code LBM070019} — claimWorkItem 업무 예외</li>
   *   <li>{@code LBM070020} — 기타 예외</li>
   * </ul>
   */
  @Override
  public BulkAssignResponse assignBulk(@RequestBody BulkAssignRequest request) throws Exception {
    List<BulkAssignRequestItem> items = request == null ? null : request.getBswrList();
    EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
    String actorEndpoint = trimToNull(header == null ? null : header.getEmnb());
    String belnOrgnCode = trimToNull(header == null ? null : header.getBelnOrgnCode());
    String commonError = request == null
        ? "LBM070001"
        : items == null || items.isEmpty()
            ? "LBM070002"
            : actorEndpoint == null
                ? "LBM070003"
                : belnOrgnCode == null ? "LBM070015" : null;

    if (commonError != null) {
      return failedBulkAssignResponse(items, commonError);
    }
    if (!isReassignAuthorized(actorEndpoint)) {
      return failedBulkAssignResponse(items, "LBM070014");
    }

    List<BulkAssignResponseItem> failures = new ArrayList<>();
    Set<String> seenTaskIds = new HashSet<>();
    Map<String, UserSearchResponse> handlers = new HashMap<>();
    int successCount = 0;

    for (BulkAssignRequestItem item : items) {
      String taskId = item == null ? null : trimToNull(item.getFncgBpmTaskLstId());
      String targetEndpoint = item == null ? null : trimToNull(item.getHndrEmnb());
      if (taskId == null) {
        addBulkAssignFailure(failures, item, "LBM070006");
        continue;
      }
      if (!seenTaskIds.add(taskId)) {
        addBulkAssignFailure(failures, item, "LBM070007");
        continue;
      }
      if (!isNumeric(taskId)) {
        addBulkAssignFailure(failures, item, "LBM070008");
        continue;
      }
      if (targetEndpoint == null) {
        addBulkAssignFailure(failures, item, "LBM070004");
        continue;
      }

      UserSearchResponse handler = handlers.get(targetEndpoint);
      if (handler == null) {
        try {
          handler = ExternalIAMService.getDefault().getUser(targetEndpoint);
        } catch (Exception exception) {
          handler = null;
        }
        if (handler == null || trimToNull(handler.getHndrEmnb()) == null) {
          addBulkAssignFailure(failures, item, "LBM070005");
          continue;
        }
        handlers.put(targetEndpoint, handler);
      }

      try {
        bulkAssignItemService.assign(
            item, targetEndpoint, trimToNull(handler.getHndrNm()), belnOrgnCode);
        successCount++;
      } catch (BulkAssignItemService.BulkAssignItemException exception) {
        addBulkAssignFailure(failures, item, exception.getResultCode());
      } catch (Exception exception) {
        addBulkAssignFailure(failures, item, "LBM070020");
      }
    }

    BulkAssignResponse response = new BulkAssignResponse();
    response.setSucsCont(successCount);
    response.setFailCont(failures.size());
    response.setFailList(failures);
    return response;
  }

  private static BulkAssignResponse failedBulkAssignResponse(
      List<BulkAssignRequestItem> items,
      String reason) {
    List<BulkAssignResponseItem> failures = new ArrayList<>();
    if (items != null) {
      for (BulkAssignRequestItem item : items) {
        addBulkAssignFailure(failures, item, reason);
      }
    }
    if (failures.isEmpty()) {
      addBulkAssignFailure(failures, null, reason);
    }
    BulkAssignResponse response = new BulkAssignResponse();
    response.setSucsCont(0);
    response.setFailCont(failures.size());
    response.setFailList(failures);
    return response;
  }

  private static void addBulkAssignFailure(
      List<BulkAssignResponseItem> failures,
      BulkAssignRequestItem source,
      String reason) {
    BulkAssignResponseItem failure = new BulkAssignResponseItem();
    if (source != null) {
      failure.setFncgBpmTaskLstId(source.getFncgBpmTaskLstId());
      failure.setFncgBpmPcesIntcId(source.getFncgBpmPcesIntcId());
    }
    failure.setPrcsRsltCntn(reason);
    failures.add(failure);
  }

  private static boolean isNumeric(String value) {
    try {
      Long.parseLong(value);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  /**
   * 다중 업무 담당자 변경 — 권한자가 업무별 처리자·기관을 지정 (본인 업무 조건 없음).
   *
   * <p>처리자 사번·기관은 body 각 항목 {@code hndrEmnb} / {@code hndrOrgnCode},
   * 요청자 사번은 ESB header.emnb.
   * 건별 성공/실패를 독립 처리하므로 바깥 {@code @Transactional} 을 두지 않는다
   * (내부 {@code reassignWorkItem} 예외 시 rollback-only → UnexpectedRollbackException 방지).</p>
   * 처리결과 코드는 {@code failList[].prcsRsltCntn}({@code LBM06XXXX}).
   * ESB header {@code prcsRsltDvsnCode} 는 성공 {@code 0} / 시스템실패 {@code 1}.
   * <ul>
   *   <li>{@code LBM060001} — request body 없음</li>
   *   <li>{@code LBM060002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM060003} — header.emnb 없음</li>
   *   <li>{@code LBM060004} — 요청자가 권한자가 아님</li>
   *   <li>{@code LBM060005} — fncgBpmTaskLstId 없음</li>
   *   <li>{@code LBM060006} — hndrEmnb 없음</li>
   *   <li>{@code LBM060007} — hndrOrgnCode 없음</li>
   *   <li>{@code LBM060008} — 요청 내 fncgBpmTaskLstId 중복</li>
   *   <li>{@code LBM060009} — fncgBpmTaskLstId 비숫자</li>
   *   <li>{@code LBM060010} — work item 없음</li>
   *   <li>{@code LBM060011} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM060012} — 진행중 아님(status not in NEW,RUNNING)</li>
   *   <li>{@code LBM060013} — 레인 roleName 없음</li>
   *   <li>{@code LBM060019} — reassignWorkItem 업무 예외</li>
   *   <li>{@code LBM060020} — 기타 예외</li>
   * </ul>
   */
  @Override
  public ReassignResponse reassignWorkItems(@RequestBody ReassignRequest request)
      throws Exception {
    List<ReassignRequestItem> bswrList = request == null ? null : request.getBswrList();
    EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
    String actorEmnb = trimToNull(header != null ? header.getEmnb() : null);

    String commonError = resolveCommonReassignError(request, bswrList, actorEmnb);
    if (commonError != null) {
      return failedReassignResponse(bswrList, commonError);
    }
    if (!isReassignAuthorized(actorEmnb)) {
      return failedReassignResponse(bswrList, "LBM060004");
    }

    List<ReassignResponseItem> failList = new ArrayList<>();
    Set<String> seenTaskIds = new HashSet<>();
    int successCount = 0;

    for (ReassignRequestItem item : bswrList) {
      String taskId = item == null ? null : trimToNull(item.getFncgBpmTaskLstId());
      if (taskId == null) {
        failList.add(reassignFailure(item, "LBM060005"));
        continue;
      }
      String hndrEmnb = item == null ? null : trimToNull(item.getHndrEmnb());
      if (hndrEmnb == null) {
        failList.add(reassignFailure(item, "LBM060006"));
        continue;
      }
      String hndrOrgnCode = item == null ? null : trimToNull(item.getHndrOrgnCode());
      if (hndrOrgnCode == null) {
        failList.add(reassignFailure(item, "LBM060007"));
        continue;
      }
      if (!seenTaskIds.add(taskId)) {
        failList.add(reassignFailure(item, "LBM060008"));
        continue;
      }

      try {
        WorklistEntity worklist = worklistRepository.findById(Long.parseLong(taskId)).orElse(null);
        if (worklist == null) {
          failList.add(reassignFailure(item, "LBM060010"));
          continue;
        }
        String validationError = validateReassignRequest(worklist, item);
        if (validationError != null) {
          failList.add(reassignFailure(item, validationError));
          continue;
        }

        RoleMappingCommand mapping = new RoleMappingCommand();
        mapping.setEndpoint(hndrEmnb);
        mapping.setGroupName(hndrOrgnCode);
        String resourceName = resolveAssigneeResourceName(hndrEmnb);
        if (resourceName != null) {
          mapping.setResourceName(resourceName);
        }
        instanceService.reassignWorkItem(taskId, mapping);
        successCount++;
      } catch (NumberFormatException e) {
        failList.add(reassignFailure(item, "LBM060009"));
      } catch (ResponseStatusException e) {
        log.warn("[reassignWorkItems] LBM060019 taskId={} status={} reason={}",
            taskId, e.getStatusCode(), e.getReason());
        failList.add(reassignFailure(item, "LBM060019"));
      } catch (Exception e) {
        log.warn("[reassignWorkItems] LBM060020 taskId={} hndrEmnb={} hndrOrgnCode={} fncgBpmPcesIntcId={}",
            taskId, hndrEmnb, hndrOrgnCode,
            item != null ? item.getFncgBpmPcesIntcId() : null,
            e);
        failList.add(reassignFailure(item, "LBM060020"));
      }
    }

    return toReassignResponse(successCount, failList);
  }

  /**
   * 요청 전체 공통 실패 코드.
   *
   * <ul>
   *   <li>{@code LBM060001} — request body 없음</li>
   *   <li>{@code LBM060002} — bswrList 없음/비어 있음</li>
   *   <li>{@code LBM060003} — header.emnb 없음</li>
   * </ul>
   */
  private static String resolveCommonReassignError(
      ReassignRequest request,
      List<ReassignRequestItem> bswrList,
      String actorEmnb) {
    if (request == null) {
      return "LBM060001";
    }
    if (bswrList == null || bswrList.isEmpty()) {
      return "LBM060002";
    }
    if (actorEmnb == null) {
      return "LBM060003";
    }
    return null;
  }

  /**
   * 일괄배정·다중 담당자 변경 권한자 여부.
   *
   * <p>요청자 사번({@code header.emnb})으로 ESB 권한 조회 예정.
   * 연동 전 임시: 사번이 {@code ESB}로 시작하면 권한 없음으로 처리한다.</p>
   */
  private boolean isReassignAuthorized(String actorEmnb) {
    if (actorEmnb != null && actorEmnb.startsWith("ESB")) {
      return false;
    }
    // TODO: ESB 권한자 조회 연동 후 반영
    // java.util.Map<String, String> payload = java.util.Map.of("emnb", actorEmnb);
    // XxxAuthResponse response = esbClient.send("ITFC_ID", "RCVE_SRVC_ID", payload, XxxAuthResponse.class);
    // return response != null && response.isAuthorized();
    return true;
  }

  /**
   * 처리자 표시명 조회 — {@link org.uengine.five.service.IAMCompanyRoleMapping#doFill()} (flyweight cache).
   *
   * <p>ESB/hwlife 경로에서는 {@code iam.provider=external} 로 Keycloak Admin API 호출을 피한다.</p>
   */
  private static String resolveAssigneeResourceName(String hndrEmnb) {
    String endpoint = trimToNull(hndrEmnb);
    if (endpoint == null) {
      return null;
    }
    try {
      RoleMapping roleMapping = RoleMapping.create();
      if (roleMapping == null) {
        return null;
      }
      roleMapping.setEndpoint(endpoint);
      roleMapping.fill();
      return trimToNull(roleMapping.getResourceName());
    } catch (Exception ignore) {
      return null;
    }
  }

  /**
   * 건별 검증 실패 코드.
   *
   * <ul>
   *   <li>{@code LBM060011} — fncgBpmPcesIntcId 불일치</li>
   *   <li>{@code LBM060012} — 진행중 아님</li>
   *   <li>{@code LBM060013} — 레인 roleName 없음</li>
   * </ul>
   */
  private static String validateReassignRequest(WorklistEntity worklist, ReassignRequestItem item) {
    String requestedInstanceId = item == null ? null : trimToNull(item.getFncgBpmPcesIntcId());
    if (requestedInstanceId != null
        && !requestedInstanceId.equals(String.valueOf(worklist.getInstId()))) {
      return "LBM060011";
    }
    String status = trimToNull(worklist.getStatus());
    if (!"NEW".equalsIgnoreCase(status) && !"RUNNING".equalsIgnoreCase(status)) {
      return "LBM060012";
    }
    if (trimToNull(worklist.getRoleName()) == null) {
      return "LBM060013";
    }
    return null;
  }

  /** 공통 사유 코드로 요청 태스크 전부를 실패 처리한다. */
  private static ReassignResponse failedReassignResponse(
      List<ReassignRequestItem> bswrList,
      String reason) {
    List<ReassignResponseItem> failList = new ArrayList<>();
    if (bswrList != null) {
      for (ReassignRequestItem item : bswrList) {
        failList.add(reassignFailure(item, reason));
      }
    }
    return toReassignResponse(0, failList);
  }

  private static ReassignResponse toReassignResponse(int successCount, List<ReassignResponseItem> failList) {
    ReassignResponse response = new ReassignResponse();
    response.setSucsCont(successCount);
    response.setFailCont(failList == null ? 0 : failList.size());
    response.setFailList(failList == null ? new ArrayList<>() : failList);
    return response;
  }

  private static ReassignResponseItem reassignFailure(ReassignRequestItem source, String reason) {
    ReassignResponseItem failure = new ReassignResponseItem();
    if (source != null) {
      failure.setFncgBpmTaskLstId(source.getFncgBpmTaskLstId());
      failure.setFncgBpmPcesIntcId(source.getFncgBpmPcesIntcId());
    }
    failure.setPrcsRsltCntn(reason);
    return failure;
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
