package org.uengine.five.notification;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.NotificationEntity;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;

/**
 * 워크아이템 배정 알림 서비스.
 *
 * <p>이름을 {@code NotificationService} 로 하지 않은 이유: uengine-core 에 동명의 빈 스텁
 * {@code org.uengine.kernel.bpmn.NotificationService} 가 이미 있어 혼동되기 때문이다.</p>
 *
 * <p>Process-GPT(Supabase) 의 Postgres 트리거와의 대응:</p>
 * <table border="1">
 *   <tr><th>참고 구현</th><th>여기</th></tr>
 *   <tr><td>{@code handle_todolist_change()}</td><td>{@link #onTaskAssigned}</td></tr>
 *   <tr><td>{@code update_notification_user_id()}</td><td>{@link #onTaskAssignmentChanged}</td></tr>
 *   <tr><td>{@code delete_notification_on_todolist_delete()}</td><td>{@link #onTaskTerminated}</td></tr>
 * </table>
 */
@Service
public class WorkItemNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemNotificationService.class);

    private static final Boolean UNCHECKED = Boolean.FALSE;

    /**
     * 이 상태로 만들어지는 워크아이템은 알림 대상이 아니다.
     * ServiceTask/ScriptActivity 는 {@code InstanceDataAppendingActivityFilter} 가
     * status=COMPLETED 로 addWorkItem 한 뒤 곧바로 completeWorkItem 하므로,
     * 막지 않으면 아무도 못 보는 알림 행이 만들어졌다 지워진다.
     */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "CANCELLED", "COMPENSATED", "DELEGATED");

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired(required = false)
    ProcessInstanceRepository processInstanceRepository;

    /** 알림 기능 전체 on/off */
    @Value("${uengine.notification.enabled:true}")
    boolean enabled;

    /**
     * true 면 배정 대상이 행위자 본인일 때 알림을 생략한다.
     * 참고 구현(DB 트리거)은 생략하지 않으므로 기본값은 false 로 맞춘다.
     */
    @Value("${uengine.notification.skip-self:false}")
    boolean skipSelf;

    // ──────────────────────────────────────────────────────────────────────
    // 생성 / 정리
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 업무가 담당자에게 배정될 때 알림을 생성한다.
     *
     * <p>같은 (taskId, userId) 로 미확인 알림이 이미 있으면 건너뛴다.
     * {@code JPAWorkList.addWorkItemImpl} 이 save 를 두 번 하고 claim 경로도 같은 훅을 타므로
     * 멱등성이 필요하다.</p>
     */
    public void onTaskAssigned(WorklistEntity wl) {
        if (!enabled || wl == null) {
            return;
        }
        String userId = trimToNull(wl.getEndpoint());
        if (userId == null || wl.getTaskId() == null) {
            return;
        }

        if (isTerminal(wl.getStatus())) {
            log.debug("[Notification] skip terminal workitem | taskId={} status={}",
                    wl.getTaskId(), wl.getStatus());
            return;
        }

        String actor = currentUserId();
        if (skipSelf && userId.equals(actor)) {
            log.debug("[Notification] skip self-assigned | taskId={} user={}", wl.getTaskId(), userId);
            return;
        }

        List<NotificationEntity> existing =
                notificationRepository.findByTaskIdAndUserIdAndIsChecked(wl.getTaskId(), userId, UNCHECKED);
        if (!existing.isEmpty()) {
            log.debug("[Notification] already notified | taskId={} user={}", wl.getTaskId(), userId);
            return;
        }

        NotificationEntity noti = new NotificationEntity();
        noti.setId(UUID.randomUUID().toString());
        noti.setUserId(userId);
        noti.setFromUserId(actor != null && !actor.equals(userId) ? actor : null);
        noti.setTitle(firstNonBlank(wl.getTitle(), wl.getDefName(), "새 할 일"));
        noti.setDescription(resolveDescription(wl));
        noti.setType(wl.getInstId() != null ? NotificationEntity.TYPE_WORKITEM_BPM
                                            : NotificationEntity.TYPE_WORKITEM);
        noti.setUrl("/todolist/" + wl.getTaskId());
        noti.setIsChecked(Boolean.FALSE);
        noti.setTimeStamp(new Date());
        noti.setTaskId(wl.getTaskId());
        noti.setInstId(wl.getInstId());

        notificationRepository.save(noti);

        log.debug("[Notification] created | taskId={} user={} url={}",
                wl.getTaskId(), userId, noti.getUrl());
    }

    /**
     * 담당자가 바뀔 때(위임·재배정) 이전 담당자의 알림을 정리하고 새 담당자에게 생성한다.
     */
    public void onTaskAssignmentChanged(WorklistEntity wl, String previousEndpoint) {
        if (!enabled || wl == null || wl.getTaskId() == null) {
            return;
        }
        String previous = trimToNull(previousEndpoint);
        if (previous != null) {
            markChecked(notificationRepository.findByTaskIdAndUserIdAndIsChecked(
                    wl.getTaskId(), previous, UNCHECKED));
        }
        onTaskAssigned(wl);
    }

    /**
     * 업무 종료(완료·스킵·취소·보상) 시 해당 업무의 미확인 알림을 모두 읽음 처리한다.
     *
     * <p>참고 구현에는 없는 동작이다. 참고 구현은 todolist 행이 <b>삭제</b>될 때만 알림을 지우므로
     * 이미 끝난 업무의 알림이 벨에 남는다.</p>
     */
    public void onTaskTerminated(WorklistEntity wl) {
        if (!enabled || wl == null || wl.getTaskId() == null) {
            return;
        }
        markChecked(notificationRepository.findByTaskIdAndIsChecked(wl.getTaskId(), UNCHECKED));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 조회 / 읽음 처리 (REST 지원)
    // ──────────────────────────────────────────────────────────────────────

    public List<NotificationEntity> listUnread(String userId) {
        if (trimToNull(userId) == null) {
            return List.of();
        }
        return notificationRepository.findTop50ByUserIdAndIsCheckedOrderByTimeStampDesc(userId, UNCHECKED);
    }

    /**
     * 읽음 처리. url 이 있으면 같은 url 의 미확인 건을 일괄 처리한다
     * (참고 구현 {@code setNotifications} 와 동일한 동작 — 같은 대상에 대한 중복 알림을 한 번에 정리).
     * url 이 없으면 id 단건만 처리한다.
     *
     * @return 읽음 처리된 건수
     */
    public int markRead(String userId, String id, String url) {
        if (trimToNull(userId) == null) {
            return 0;
        }
        if (trimToNull(url) != null) {
            return markChecked(notificationRepository.findByUserIdAndUrlAndIsChecked(userId, url, UNCHECKED));
        }
        if (trimToNull(id) == null) {
            return 0;
        }
        return notificationRepository.findById(id)
                .filter(n -> userId.equals(n.getUserId()))
                .map(n -> markChecked(List.of(n)))
                .orElse(0);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 내부
    // ──────────────────────────────────────────────────────────────────────

    private int markChecked(List<NotificationEntity> targets) {
        if (targets == null || targets.isEmpty()) {
            return 0;
        }
        for (NotificationEntity n : targets) {
            n.setIsChecked(Boolean.TRUE);
        }
        notificationRepository.saveAll(targets);
        return targets.size();
    }

    /** 부제목: 프로세스 인스턴스 명 → 정의 명 → 업무 제목 순으로 대체한다. */
    private String resolveDescription(WorklistEntity wl) {
        if (wl.getInstId() != null && processInstanceRepository != null) {
            try {
                ProcessInstanceEntity pi = processInstanceRepository.findById(wl.getInstId()).orElse(null);
                if (pi != null && trimToNull(pi.getName()) != null) {
                    return pi.getName();
                }
            } catch (Exception e) {
                log.debug("[Notification] failed to resolve process instance name | instId={}", wl.getInstId(), e);
            }
        }
        return firstNonBlank(wl.getDefName(), wl.getTitle());
    }

    private String currentUserId() {
        try {
            UserContext uc = UserContext.getThreadLocalInstance();
            return uc != null ? trimToNull(uc.getUserId()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isTerminal(String status) {
        return status != null && TERMINAL_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            String t = trimToNull(v);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
