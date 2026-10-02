package org.uengine.hwlife.iam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactional;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.five.service.WorkItemAssignmentStateService;
import org.uengine.hwlife.iam.dto.ChangedIamSyncRequest;
import org.uengine.hwlife.iam.dto.ChangedIamSyncRequestItem;
import org.uengine.hwlife.iam.dto.ChangedIamSyncResponse;
import org.uengine.hwlife.iam.dto.ChangedIamSyncResponseItem;
import org.uengine.hwlife.iam.dto.RoleSearchResponse;
import org.uengine.hwlife.iam.dto.UserSearchRequest;
import org.uengine.hwlife.iam.dto.UserSearchResponse;
import org.uengine.hwlife.iam.dto.OrgSearchResponse;
import org.uengine.kernel.Activity;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleMapping;

/**
 * {@link IAMIntegrationService} REST 구현. {@link ExternalIAMService}를 통해 외부 IAM을 조회합니다.
 */
@RestController
public class IAMIntegrationServiceImpl implements IAMIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IAMIntegrationServiceImpl.class);

    private final ExternalIAMService externalIamService = ExternalIAMService.getDefault();
    private final ProcessInstanceRepository processInstanceRepository;
    private final WorklistRepository worklistRepository;
    private final InstanceServiceImpl instanceService;
    private final WorkItemAssignmentStateService assignmentStateService;
    /** 같은 빈 self-call 시 {@code REQUIRES_NEW}/{@code ProcessTransactional} 프록시 적용용 */
    private final IAMIntegrationServiceImpl self;

    public IAMIntegrationServiceImpl(
            ProcessInstanceRepository processInstanceRepository,
            WorklistRepository worklistRepository,
            InstanceServiceImpl instanceService,
            WorkItemAssignmentStateService assignmentStateService,
            @Lazy IAMIntegrationServiceImpl self) {
        this.processInstanceRepository = processInstanceRepository;
        this.worklistRepository = worklistRepository;
        this.instanceService = instanceService;
        this.assignmentStateService = assignmentStateService;
        this.self = self;
    }

    @Override
    public OrgSearchResponse searchOrgs() throws Exception {
        OrgSearchResponse response = new OrgSearchResponse();
        response.setBpmOrgnList(externalIamService.getGroups().getBpmOrgnList());
        return response;
    }

    @Override
    public RoleSearchResponse searchRoles() throws Exception {
        RoleSearchResponse response = new RoleSearchResponse();
        response.setBpmAtrtList(externalIamService.getRoles().getBpmAtrtList());
        return response;
    }

    @Override
    public UserSearchResponse searchUser(UserSearchRequest request) throws Exception {
        if (request == null || request.getHndrEmnb() == null || request.getHndrEmnb().isBlank()) {
            return null;
        }
        UserSearchResponse response = externalIamService.getUser(request.getHndrEmnb());
        if (response == null || response.getHndrEmnb() == null || response.getHndrEmnb().isBlank()) {
            return null;
        }
        return response;
    }

    /**
     * 기관 통폐합 등 IAM 변경을 진행 중 인스턴스 RoleMapping·진행중 단위업무에 반영.
     *
     * <p>집계 단위는 요청 {@code chngList} 의 대출계약번호 1건이다.
     * 해당 대출의 진행중 인스턴스를 모두 성공해야 성공 1건, 인스턴스 1건이라도 실패하면
     * 실패 1건·{@code failList} 에 해당 대출계약번호만 추가한다.
     * {@code failList[].prcsRsltCntn} 에는 실패한 인스턴스와 사유를 담는다.</p>
     */
    @Override
    public ChangedIamSyncResponse syncChangedIam(ChangedIamSyncRequest request) throws Exception {
        ChangedIamSyncResponse response = new ChangedIamSyncResponse();
        List<ChangedIamSyncResponseItem> failList = new ArrayList<>();
        int successCount = 0;

        if (request == null || request.getChngList() == null || request.getChngList().isEmpty()) {
            response.setSucsCont(0);
            response.setFailCont(0);
            response.setFailList(failList);
            return response;
        }

        for (ChangedIamSyncRequestItem item : request.getChngList()) {
            if (item == null) {
                failList.add(failItem(null, failContent(null, "Missing required request item")));
                continue;
            }
            String loanCntcNo = trimToNull(item.getLoanCntcNo());
            String beforeOrg = trimToNull(item.getChngBefrFncgWndwOrgnCode());
            String afterOrg = trimToNull(item.getChngAfeqFncgWndwOrgnCode());

            if (loanCntcNo == null || beforeOrg == null || afterOrg == null) {
                failList.add(failItem(loanCntcNo, failContent(null,
                        "Missing required fields(loanCntcNo/chngBefrFncgWndwOrgnCode/chngAftFncgWndwOrgnCode)")));
                continue;
            }
            if (beforeOrg.equals(afterOrg)) {
                failList.add(failItem(loanCntcNo, failContent(null, "Before and after org codes are identical")));
                continue;
            }

            try {
                self.syncOne(loanCntcNo, beforeOrg, afterOrg);
                successCount++;
            } catch (IllegalStateException e) {
                failList.add(failItem(loanCntcNo, e.getMessage()));
            } catch (Exception e) {
                log.error("[IAM-sync] 기관변경 반영 실패 loanCntcNo={} before={} after={}",
                    loanCntcNo, beforeOrg, afterOrg, e);
                failList.add(failItem(loanCntcNo, failContent(null, e.getMessage())));
            }
        }

        response.setSucsCont(successCount);
        response.setFailCont(failList.size());
        response.setFailList(failList);
        return response;
    }

    /**
     * 대출계약번호 1건 반영. 진행중 인스턴스가 여러 개면 전부 성공해야 하며,
     * 하나라도 실패하면 예외로 전체 롤백한다(호출측 실패 1건·failList 1건).
     * <ul>
     *   <li>각 진행중 인스턴스 RoleMapping.groupName == 변경전 → 변경후 (이미 변경후면 성공)</li>
     *   <li>진행중 단위업무 groupCd == 변경전 → 변경후 (있으면; 이미 변경후면 유지)</li>
     *   <li>종료된 단위업무는 수정하지 않음</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @ProcessTransactional
    public void syncOne(String loanCntcNo, String beforeOrg, String afterOrg) throws Exception {
        List<ProcessInstanceEntity> instances = processInstanceRepository
                .findByLoanCntcNoAndStatus(loanCntcNo, Activity.STATUS_RUNNING);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException(failContent(null, "No running instance"));
        }

        Set<Long> processedRoots = new HashSet<>();
        int instanceCount = 0;

        for (ProcessInstanceEntity entity : instances) {
            if (entity == null || entity.getInstId() == null) {
                throw new IllegalStateException(failContent(null, "Instance info is missing"));
            }
            Long instId = entity.getInstId();

            try {
                // 인스턴스별 RoleMapping: 변경전→변경후, 또는 이미 변경후면 성공
                if (!updateRoleMappings(instId, beforeOrg, afterOrg)) {
                    throw new IllegalStateException(failContent(instId, "RoleMapping.groupName mismatch"));
                }
                instanceCount++;

                Long rootInstId = entity.getRootInstId() != null ? entity.getRootInstId() : instId;
                if (!processedRoots.add(rootInstId)) {
                    continue;
                }
                List<WorklistEntity> active = worklistRepository.findActiveByRootOrInstance(rootInstId);
                if (updateActiveWorkItems(active, beforeOrg, afterOrg)) {
                    assignmentStateService.synchronize(rootInstId);
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(failContent(instId, e.getMessage()), e);
            }
        }

        if (instanceCount == 0) {
            throw new IllegalStateException(failContent(null, "No running instance"));
        }
    }

    /**
     * RoleMapping.groupName 반영.
     * <ul>
     *   <li>변경전 → 변경후 갱신</li>
     *   <li>이미 변경후면 성공(재요청·신규 업무)</li>
     * </ul>
     *
     * @return 변경전을 갱신했거나, 이미 변경후인 RoleMapping 이 있으면 true
     */
    private boolean updateRoleMappings(Long instId, String beforeOrg, String afterOrg) throws Exception {
        ProcessInstance instance = instanceService.getProcessInstanceLocal(String.valueOf(instId));
        if (!(instance instanceof org.uengine.kernel.DefaultProcessInstance)) {
            return false;
        }

        Map<?, ?> variables = ((org.uengine.kernel.DefaultProcessInstance) instance).getVariables();
        if (variables == null || variables.isEmpty()) {
            return false;
        }

        boolean updated = false;
        boolean alreadyAfter = false;
        for (Object value : variables.values()) {
            if (!(value instanceof RoleMapping)) {
                continue;
            }
            RoleMapping mapping = (RoleMapping) value;
            String roleName = mapping.getName();
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            String groupName = mapping.getGroupName();
            if (beforeOrg.equals(groupName)) {
                mapping.setGroupName(afterOrg);
                instance.putRoleMapping(roleName, mapping);
                updated = true;
            } else if (afterOrg.equals(groupName)) {
                alreadyAfter = true;
            }
        }
        return updated || alreadyAfter;
    }

    /**
     * 진행중(NEW/RUNNING) 단위업무만 groupCd 변경. 종료 건은 목록에 없음.
     *
     * @return 실제 변경된 workitem 이 있으면 true
     */
    private boolean updateActiveWorkItems(List<WorklistEntity> active, String beforeOrg, String afterOrg) {
        if (active == null || active.isEmpty()) {
            return false;
        }
        List<WorklistEntity> toSave = new ArrayList<>();
        for (WorklistEntity workitem : active) {
            if (workitem == null) {
                continue;
            }
            if (beforeOrg.equals(workitem.getGroupCd())) {
                workitem.setGroupCd(afterOrg);
                toSave.add(workitem);
            }
        }
        if (toSave.isEmpty()) {
            return false;
        }
        worklistRepository.saveAll(toSave);
        return true;
    }

    private static ChangedIamSyncResponseItem failItem(String loanCntcNo, String reason) {
        ChangedIamSyncResponseItem item = new ChangedIamSyncResponseItem();
        item.setLoanCntcNo(loanCntcNo);
        item.setPrcsRsltCntn(reason);
        return item;
    }

    /** failList.prcsRsltCntn — failed instance and reason */
    private static String failContent(Long instId, String reason) {
        String id = instId != null ? String.valueOf(instId) : "-";
        String detail = reason != null && !reason.isBlank() ? reason : "Unknown error";
        return "failedInstId=" + id + ", reason=" + detail;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
