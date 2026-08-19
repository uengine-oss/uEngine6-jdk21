package org.uengine.five.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

import org.uengine.five.entity.converter.OracleBooleanConverter;

/**
 * 사용자 알림(헤더 벨/배지) 엔티티.
 *
 * <p>Process-GPT(Supabase) 의 {@code public.notifications} 테이블과 같은 의미를 갖는다.
 * 프론트엔드(NotificationDD.vue)가 snake_case 필드명을 그대로 읽으므로
 * REST 응답 필드명은 {@code org.uengine.five.notification.NotificationResource} 에서 고정한다.</p>
 *
 * <p>알림 생성/정리는 {@code org.uengine.five.lifecycle.BpmLifecycleService} 의
 * onTaskAssigned / onTaskAssignmentChanged / onTaskTerminated 훅에서 이루어진다.</p>
 */
@Entity
@Table(name = "BPM_NOTIFICATION")
public class NotificationEntity {

    /** 알림 타입: BPM 프로세스에 속한 워크아이템 (프론트가 url 로 라우팅) */
    public static final String TYPE_WORKITEM_BPM = "workitem_bpm";

    /** 알림 타입: 프로세스 인스턴스가 없는 단독 워크아이템 (프론트가 /todolist 목록으로 라우팅) */
    public static final String TYPE_WORKITEM = "workitem";

    @Id
    @Column(name = "id", length = 36)
    private String id;

    /** 수신자. WorklistEntity.endpoint (= JWT email) 와 동일한 값. */
    @Column(name = "user_id", length = 255)
    private String userId;

    /** 알림을 유발한 행위자. nullable. */
    @Column(name = "from_user_id", length = 255)
    private String fromUserId;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "type", length = 50)
    private String type;

    /** 클릭 시 이동할 프론트엔드 경로. 워크아이템은 {@code /todolist/{taskId}}. */
    @Column(name = "url", length = 1000)
    private String url;

    /** 읽음 여부. false = 미확인(배지에 노출). */
    @Column(name = "is_checked")
    @Convert(converter = OracleBooleanConverter.class)
    private Boolean isChecked;

    @Column(name = "time_stamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Date timeStamp;

    /** 상관키. 참고 구현은 url 문자열을 파싱했지만 여기서는 실제 컬럼으로 둔다. */
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "inst_id")
    private Long instId;

    /** 멀티테넌시 예약 컬럼. 현재 채우지 않는다. */
    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getIsChecked() {
        return isChecked;
    }

    public void setIsChecked(Boolean isChecked) {
        this.isChecked = isChecked;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getInstId() {
        return instId;
    }

    public void setInstId(Long instId) {
        this.instId = instId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
