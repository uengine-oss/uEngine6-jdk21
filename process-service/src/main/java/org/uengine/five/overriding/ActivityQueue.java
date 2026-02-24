package org.uengine.five.overriding;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.uengine.five.events.ActivityInfo;
import org.uengine.five.events.ActivityQueued;
import org.uengine.kernel.IActivityEventQueue;

/**
 * Created by uengine on 2018. 11. 16..
 */
public class ActivityQueue implements IActivityEventQueue {

    private final StreamBridge streamBridge;

    public ActivityQueue(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void queue(String instanceId, String tracingTag, int retryingCount, String[] additionalParameters) {
        ActivityQueued activityQueued = new ActivityQueued();
        activityQueued.setActivityInfo(new ActivityInfo());
        activityQueued.getActivityInfo().setInstanceId(instanceId);
        activityQueued.getActivityInfo().setTracingTag(tracingTag);

        streamBridge.send("bpm-out", MessageBuilder
                .withPayload(activityQueued)
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .build());
    }
}
