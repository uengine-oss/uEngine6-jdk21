package org.uengine.five.notification;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.contexts.UserContext;
import org.uengine.five.framework.ProcessTransactional;

/**
 * 헤더 알림(벨/배지) REST API.
 *
 * <pre>
 * GET  /notifications        현재 사용자의 미확인 알림 (최신순, 최대 50)
 * POST /notifications/read   {"id": "...", "url": "/todolist/123"} 읽음 처리
 * </pre>
 *
 * <p>수신자는 <b>토큰에서만</b> 판별한다. userId 를 파라미터로 받지 않으므로
 * 남의 알림을 조회/변경할 수 없다. ({@code SecurityAwareServletFilter} 가 JWT 의
 * email → preferred_username → sub 순으로 {@code UserContext} 를 채운다.)</p>
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    WorkItemNotificationService workItemNotificationService;

    @GetMapping
    public List<NotificationResource> list() {
        String userId = currentUserId();
        if (userId == null) {
            return List.of();
        }
        return workItemNotificationService.listUnread(userId).stream()
                .map(NotificationResource::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/read")
    @ProcessTransactional
    public ResponseEntity<Map<String, Object>> read(@RequestBody(required = false) Map<String, Object> body) {
        String userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        String id = body == null ? null : asString(body.get("id"));
        String url = body == null ? null : asString(body.get("url"));

        int updated = workItemNotificationService.markRead(userId, id, url);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private static String currentUserId() {
        UserContext uc = UserContext.getThreadLocalInstance();
        if (uc == null || uc.getUserId() == null || uc.getUserId().trim().isEmpty()) {
            return null;
        }
        return uc.getUserId();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
