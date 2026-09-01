package org.uengine.five.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
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

    @Value("${esb.lifecycle.enabled:false}")
    private boolean enabled;


    public BpmAssignmentNotifyService(@Autowired(required = false) EsbClient esbClient) {
        this.esbClient = esbClient;
    }

    /**
     * 업무가 담당자에게 최초로 배정될 때 호출.
     * endpoint 가 null/empty 면 아직 확정되지 않은 상태이므로 송신하지 않는다.
     */
    public void notifyAssigned(WorklistEntity wl, ProcessInstanceEntity pi) {
        if (wl == null) {
            return;
        }

        BpmLifecycleEventRequest event = new BpmLifecycleEventRequest();
        event.setLoanPcesMgmtNo(pi == null ? null : pi.getCorrKey());
        event.setFncgBpmTaskTrcgNm(wl.getTrcTag());
        event.setFncgBpmUworSttsCntn(wl.getStatus());
        event.setPrgsSttsNm(pi == null ? null : pi.getStatus());
        event.setHndrEmnb(wl.getEndpoint());
        event.setApvlYn(wl.getApvlYn() == null ? "N" : wl.getApvlYn() ? "Y" : "N");
        event.setImgeScanYn(wl.getImgeScanYn() == null ? "N" : wl.getImgeScanYn() ? "Y" : "N");

        if (!enabled) {
            log.trace("[BpmAssignment] ESB send skipped (esb.assignment.enabled=false) | {}", event);
            return;
        }
        log.debug("[BpmAssignment] ESB send ok | {}", event);

        sendWorkItemAssignmentNotify(event);
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
