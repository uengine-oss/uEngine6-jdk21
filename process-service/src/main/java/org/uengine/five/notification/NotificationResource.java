package org.uengine.five.notification;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.uengine.five.entity.NotificationEntity;

/**
 * 알림 REST 응답 DTO.
 *
 * <p>필드명은 프론트엔드(NotificationDD.vue / UEngineBackend.fetchNotifications)가
 * Supabase 테이블 컬럼명을 그대로 읽으므로 <b>snake_case 로 고정</b>한다.
 * ({@code noti.new.is_checked}, {@code noti.new.user_id}, {@code item.time_stamp})</p>
 */
public class NotificationResource {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC);

    @JsonProperty("id")
    private String id;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("from_user_id")
    private String fromUserId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("url")
    private String url;

    @JsonProperty("is_checked")
    private boolean checked;

    /** ISO-8601 문자열. 프론트가 {@code new Date(item.time_stamp)} 로 파싱한다. */
    @JsonProperty("time_stamp")
    private String timeStamp;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("inst_id")
    private Long instId;

    public static NotificationResource from(NotificationEntity e) {
        NotificationResource r = new NotificationResource();
        r.id = e.getId();
        r.userId = e.getUserId();
        r.fromUserId = e.getFromUserId();
        r.type = e.getType();
        r.title = e.getTitle();
        r.description = e.getDescription();
        r.url = e.getUrl();
        r.checked = Boolean.TRUE.equals(e.getIsChecked());
        r.timeStamp = e.getTimeStamp() == null
                ? null
                : ISO.format(Instant.ofEpochMilli(e.getTimeStamp().getTime()));
        r.taskId = e.getTaskId();
        r.instId = e.getInstId();
        return r;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public boolean isChecked() {
        return checked;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getInstId() {
        return instId;
    }
}
