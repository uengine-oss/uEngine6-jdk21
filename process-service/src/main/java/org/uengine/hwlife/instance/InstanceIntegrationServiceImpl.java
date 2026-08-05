package org.uengine.hwlife.instance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;
import org.uengine.hwlife.instance.dto.*;

/**
 * {@link InstanceIntegrationService} REST 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class InstanceIntegrationServiceImpl implements InstanceIntegrationService {

  private final InstanceServiceImpl instanceService;
  private final WorklistRepository worklistRepository;

  public InstanceIntegrationServiceImpl(
      InstanceServiceImpl instanceService,
      WorklistRepository worklistRepository) {
    this.instanceService = instanceService;
    this.worklistRepository = worklistRepository;
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
   */
  @Override
  @Transactional
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
        WorklistEntity worklist = worklistRepository.findByIdForUpdate(Long.parseLong(taskId)).orElse(null);
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
   * 나의 업무함 선점 조건과 동일: {@code groupCd == header.belnOrgnCode}.
   */
  private static boolean isSameOrganization(WorklistEntity worklist, String belnOrgnCode) {
    String groupCd = trimToNull(worklist.getGroupCd());
    String organization = trimToNull(belnOrgnCode);
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

  @Override
  @Transactional
  public DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request)
      throws Exception {
    throw notImplemented("delegateWorkItems");
  }

  @Override
  @Transactional
  public BulkAssignResponse assignBulk(@RequestBody BulkAssignRequest request) throws Exception {
    throw notImplemented("assignBulk");
  }

  @Override
  @Transactional
  public ReassignResponse reassignWorkItems(@RequestBody ReassignRequest request)
      throws Exception {
    throw notImplemented("reassignWorkItems");
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
