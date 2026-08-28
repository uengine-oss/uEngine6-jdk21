package org.uengine.five.lifecycle;

import org.springframework.stereotype.Service;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.five.overriding.JPAProcessInstance;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.kernel.ProcessInstance;
import org.uengine.processmanager.TransactionContext;

/**
 * BPM 업무/프로세스 생명주기 훅 진입점.
 *
 * <p>호출자는 {@link TransactionContext}(실무적으로 {@link ProcessTransactionContext})를
 * 넘기고, {@link ProcessInstanceEntity} resolve·attach 는 이 서비스가 담당한다.</p>
 *
 * <p>기능 분리:
 * <ul>
 *   <li>{@link BpmAssignmentNotifyService} — 업무 배정(할당) 알림 (현재 ESB 송신)</li>
 *   <li>{@link BpmWorkStatusNotifyService} — 현 업무 상태 알림 (구조 유지, 추후 송신)</li>
 * </ul>
 *
 * <p>호출 위치:
 * <ul>
 *   <li>{@code JPAWorkList.addWorkItemImpl}    → {@link #onTaskAssigned}</li>
 *   <li>{@code InstanceServiceImpl.claimWorkItem} → {@link #onTaskAssignmentChanged}</li>
 *   <li>{@code JPAWorkList.updateWorkItem}     → {@link #onTaskTerminated} / {@link #onTaskAssignmentChanged}</li>
 *   <li>{@code JPAWorkList.completeWorkItem}   → {@link #onTaskTerminated}</li>
 *   <li>{@code JPAWorkList.cancelWorkItem}     → {@link #onTaskTerminated}</li>
 *   <li>{@code JPAWorkList.compensateWorkItem} → {@link #onTaskTerminated}</li>
 *   <li>{@code JPAProcessInstance.setStatus("", Completed/Stopped)} → {@link #onProcessCompleted}</li>
 * </ul>
 */
@Service
public class BpmLifecycleService {

    private final BpmAssignmentNotifyService assignmentNotifyService;
    private final BpmWorkStatusNotifyService workStatusNotifyService;
    private final ProcessInstanceRepository processInstanceRepository;

    public BpmLifecycleService(
            BpmAssignmentNotifyService assignmentNotifyService,
            BpmWorkStatusNotifyService workStatusNotifyService,
            ProcessInstanceRepository processInstanceRepository) {
        this.assignmentNotifyService = assignmentNotifyService;
        this.workStatusNotifyService = workStatusNotifyService;
        this.processInstanceRepository = processInstanceRepository;
    }

    /** 업무 최초 배정 (생성). */
    public void onTaskAssigned(WorklistEntity wl, TransactionContext tc) {
        ProcessInstanceEntity pi = resolveAndAttach(wl, tc);
        assignmentNotifyService.notifyAssigned(wl, pi);
        workStatusNotifyService.notifyAssigned(wl, pi);
    }

    /** 담당자 변경 (위임·재배정·claim). */
    public void onTaskAssignmentChanged(WorklistEntity wl, TransactionContext tc) {
        ProcessInstanceEntity pi = resolveAndAttach(wl, tc);
        workStatusNotifyService.notifyAssignmentChanged(wl, pi);
    }

    /** 업무 종료. */
    public void onTaskTerminated(WorklistEntity wl, TransactionContext tc) {
        ProcessInstanceEntity pi = resolveAndAttach(wl, tc);
        workStatusNotifyService.notifyTerminated(wl, pi);
    }

    /** 메인 프로세스 인스턴스 종료. */
    public void onProcessCompleted(ProcessInstanceEntity pi) {
        workStatusNotifyService.notifyProcessCompleted(pi);
    }

    private ProcessInstanceEntity resolveAndAttach(WorklistEntity wl, TransactionContext tc) {
        ProcessInstanceEntity pi = resolveProcessInstanceEntity(wl, tc);
        attachProcessInstance(wl, pi);
        return pi;
    }

    private void attachProcessInstance(WorklistEntity wl, ProcessInstanceEntity pi) {
        if (wl != null && pi != null && wl.getProcessInstance() == null) {
            wl.setProcessInstance(pi);
        }
    }

    private ProcessInstanceEntity resolveProcessInstanceEntity(WorklistEntity wl, TransactionContext tc) {
        if (wl == null || wl.getInstId() == null) {
            return null;
        }
        if (wl.getProcessInstance() != null) {
            return wl.getProcessInstance();
        }
        ProcessInstanceEntity fromCache = resolveFromTransactionContext(wl.getInstId(), tc);
        if (fromCache != null) {
            return fromCache;
        }
        return processInstanceRepository.findById(wl.getInstId()).orElse(null);
    }

    private ProcessInstanceEntity resolveFromTransactionContext(Long instId, TransactionContext tc) {
        ProcessTransactionContext ptc = toProcessTransactionContext(tc);
        if (ptc == null) {
            return null;
        }
        ProcessInstance pi = ptc.getProcessInstanceInTransaction(String.valueOf(instId));
        if (pi instanceof JPAProcessInstance) {
            return ((JPAProcessInstance) pi).getProcessInstanceEntity();
        }
        return null;
    }

    private ProcessTransactionContext toProcessTransactionContext(TransactionContext tc) {
        if (tc instanceof ProcessTransactionContext) {
            return (ProcessTransactionContext) tc;
        }
        return ProcessTransactionContext.getThreadLocalInstance();
    }
}
