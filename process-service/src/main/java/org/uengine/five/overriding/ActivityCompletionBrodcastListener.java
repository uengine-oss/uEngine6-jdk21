package org.uengine.five.overriding;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.kernel.Activity;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.IActivityCompletionListener;
import org.uengine.kernel.ProcessInstance;

/**
 * Publishes task progress events to bpm-brodcast after commit, whenever a
 * HumanActivity completes.
 */
@Component
public class ActivityCompletionBrodcastListener implements IActivityCompletionListener {

    @Autowired
    EventPublisher eventPublisher;

    @Override
    public void onActivityCompleted(ProcessInstance instance, Activity activity) throws Exception {
        if (!(activity instanceof HumanActivity))
            return;

        HumanActivity humanActivity = (HumanActivity) activity;

        Map<String, Object> taskEvent = new HashMap<>();
        taskEvent.put("eventType", "TASK_COMPLETED");
        taskEvent.put("instanceId", instance != null ? instance.getInstanceId() : null);
        taskEvent.put("tracingTag", activity.getTracingTag());
        taskEvent.put("activityName", activity.getName());
        try {
            taskEvent.put("taskIds", humanActivity.getTaskIds(instance));
        } catch (Exception ignored) {
            // optional
        }

        eventPublisher.send("bpm-brodcast", taskEvent, Map.of("type", "TASK_COMPLETED"));
    }
}
