/**
 * 파일 역할: 태스크 SKIP 가능여부/사유를 전달하는 DTO
 *
 * 기능:
 * - GET /work-item/{taskId}/skip/availability 응답 모델
 * - enabled=false인 경우 reason으로 UI에 불가 사유를 표시
 */
package org.uengine.five.dto;

import java.util.ArrayList;
import java.util.List;

public class TaskSkipAvailability {

    boolean enabled;
    String reason;
    List<String> warnings;

    public static TaskSkipAvailability enabled() {
        TaskSkipAvailability res = new TaskSkipAvailability();
        res.setEnabled(true);
        return res;
    }

    public static TaskSkipAvailability disabled(String reason) {
        TaskSkipAvailability res = new TaskSkipAvailability();
        res.setEnabled(false);
        res.setReason(reason);
        return res;
    }

    public static TaskSkipAvailability enabledWithWarnings(List<String> warnings) {
        TaskSkipAvailability res = enabled();
        res.setWarnings(warnings);
        return res;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? null : new ArrayList<>(warnings);
    }
}

