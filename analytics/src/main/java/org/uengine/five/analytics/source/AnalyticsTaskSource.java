package org.uengine.five.analytics.source;

import java.util.Date;

public record AnalyticsTaskSource(
        Long taskId,
        Long instId,
        Long rootInstId,
        String defId,
        String defVerId,
        String defName,
        String tracingTag,
        String absoluteTracingTag,
        String title,
        String activityType,
        String tool,
        String endpoint,
        String resourceName,
        String groupCode,
        String roleName,
        Date startDate,
        Date endDate,
        Date dueDate,
        Date saveDate,
        String status,
        String decision,
        String reason,
        Integer priority,
        boolean delegated) {
}
