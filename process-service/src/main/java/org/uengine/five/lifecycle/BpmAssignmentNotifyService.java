package org.uengine.five.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.service.RootInstanceResolver;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.esbclient.exception.EsbException;

/**
 * BPM 업무 배정(할당) 알림.
 *
 * <p>업무 최초 생성·claim 확정 시 ESB로 배정 상태를 송신한다.</p>
 */
@Service
public class BpmAssignmentNotifyService {

    private static final Logger log = LoggerFactory.getLogger(BpmAssignmentNotifyService.class);

    private final EsbClient esbClient;
    private final RootInstanceResolver rootInstanceResolver;

    @Value("${esb.lifecycle.enabled:false}")
    private boolean enabled;


    public BpmAssignmentNotifyService(
            @Autowired(required = false) EsbClient esbClient,
            RootInstanceResolver rootInstanceResolver) {
        this.esbClient = esbClient;
        this.rootInstanceResolver = rootInstanceResolver;
    }

    /**
     * 업무가 담당자에게 최초로 배정될 때 호출.
     * {@code pi} 는 항상 root 인스턴스 정보로 해석해 사용한다.
     * {@code dvsnVal} 기본값은 {@code false}.
     */
    public BpmLifecycleEventResponse notifyAssigned(List<WorklistEntity> wls, ProcessInstanceEntity pi) {
        return notifyAssigned(wls, pi, false);
    }

    /**
     * 업무 배정 알림.
     *
     * @param dvsnVal ESB {@code dvsnVal} — 기본 {@code false}, sync 등에서 {@code true}
     * @return ESB 응답 ({@link BpmLifecycleEventResponse})
     */
    public BpmLifecycleEventResponse notifyAssigned(List<WorklistEntity> wls, ProcessInstanceEntity pi, boolean dvsnVal) {
        BpmLifecycleEventResponse response = new BpmLifecycleEventResponse();
        if (wls == null || wls.isEmpty()) {
            return response;
        }

        ProcessInstanceEntity rootPi = resolveRootInstance(pi);

        List<BpmLifecycleEventRequestItem> items = new ArrayList<>(wls.size());
        for (WorklistEntity wl : wls) {
            if (wl == null) {
                continue;
            }
            BpmLifecycleEventRequestItem event = new BpmLifecycleEventRequestItem();
            event.setLoanPcesMgmtNo(rootPi == null ? null : rootPi.getCorrKey());
            event.setFncgBpmTaskTrcgNm(wl.getTrcTag());
            event.setFncgBpmUworSttsCntn(wl.getStatus());
            event.setPrgsSttsNm(rootPi == null ? null : rootPi.getStatus());
            event.setHndrEmnb(wl.getEndpoint());
            event.setApvlYn(wl.getApvlYn() == null ? "N" : wl.getApvlYn() ? "Y" : "N");
            event.setImgeScanYn(wl.getImgeScanYn() == null ? "N" : wl.getImgeScanYn() ? "Y" : "N");
            items.add(event);
        }
        if (items.isEmpty()) {
            return response;
        }

        BpmLifecycleEventRequest notify = new BpmLifecycleEventRequest();
        notify.setDvsnVal(dvsnVal ? "true" : "false");
        notify.setBswrList(items);

        if (!enabled) {
            log.trace("[BpmAssignment] ESB send skipped (esb.assignment.enabled=false) | {}", notify);
            return response;
        }
        log.debug("[BpmAssignment] ESB send ok | {}", notify);

        return sendWorkItemAssignmentNotify(notify);
    }

    private ProcessInstanceEntity resolveRootInstance(ProcessInstanceEntity pi) {
        if (pi == null || pi.getInstId() == null) {
            return pi;
        }
        Long rootInstId = RootInstanceResolver.rootInstIdOf(pi);
        if (rootInstId == null || rootInstId.equals(pi.getInstId())) {
            return pi; // 이미 root
        }
        ProcessInstanceEntity root = rootInstanceResolver.resolve(rootInstId);
        return root != null ? root : pi;
    }

    private BpmLifecycleEventResponse sendWorkItemAssignmentNotify(BpmLifecycleEventRequest event) {
        BpmLifecycleEventResponse response = new BpmLifecycleEventResponse();
        if (esbClient == null) {
            log.warn("[BpmAssignment] ESB send skipped (EsbClient bean not available) | {}", event);
            return response;
        }


        try {
            response = esbClient.send("", "", event, BpmLifecycleEventResponse.class);
            log.trace("[BpmAssignment] ESB send ok | {}", event);
            return response;
        } catch (EsbException ex) {
            log.warn("[BpmAssignment] ESB send failed | event={} reason={}", event, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("[BpmAssignment] ESB send failed | event={}", event, ex);
        }
        return response;
    }
}
