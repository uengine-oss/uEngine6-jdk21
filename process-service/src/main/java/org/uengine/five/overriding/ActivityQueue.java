package org.uengine.five.overriding;

import org.uengine.five.events.ActivityInfo;
import org.uengine.five.events.ActivityQueued;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.kernel.IActivityEventQueue;

/**
 * Activity queue 이벤트 발행. Kafka/Outbox 전략에 무관하게 EventPublisher 로 위임.
 */
public class ActivityQueue implements IActivityEventQueue {

    private final EventPublisher eventPublisher;

    public ActivityQueue(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void queue(String instanceId, String tracingTag, int retryingCount, String[] additionalParameters) {
        ActivityQueued activityQueued = new ActivityQueued();
        activityQueued.setActivityInfo(new ActivityInfo());
        activityQueued.getActivityInfo().setInstanceId(instanceId);
        activityQueued.getActivityInfo().setTracingTag(tracingTag);

        eventPublisher.send("bpm-out", activityQueued);
    }
}
