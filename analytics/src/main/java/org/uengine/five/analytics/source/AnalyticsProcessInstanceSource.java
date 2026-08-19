package org.uengine.five.analytics.source;

import java.util.Date;

public record AnalyticsProcessInstanceSource(
        Long instId,
        String defId,
        String defVerId,
        String defName,
        String defPath,
        Date startedDate,
        Date finishedDate,
        Date dueDate,
        Date modDate,
        String status,
        boolean deleted,
        boolean subprocess,
        boolean eventHandler,
        Long rootInstId,
        String initiator,
        String initiatorGroupCode) {
}
