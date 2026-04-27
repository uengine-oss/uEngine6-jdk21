package org.uengine.five;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.five.events.ActivityDone;
import org.uengine.five.events.ActivityFailed;
import org.uengine.five.events.ActivityInfo;
import org.uengine.five.events.ActivityQueued;
import org.uengine.five.events.DefinitionDeployed;
import org.uengine.five.framework.ProcessTransactional;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.five.overriding.ActivityQueue;
import org.uengine.five.service.DefinitionServiceUtil;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.kernel.Activity;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ReceiveActivity;
import org.uengine.kernel.UEngineException;

@Component
public class EventListener {

    @Autowired
    InstanceServiceImpl instanceService;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    ActivityQueue activityQueue;

    /** Called from BpmMessageDispatcher (Spring Cloud Stream 4 functional). */
    public void handleDone(ActivityDone activityDone) {

        if (!activityDone.checkMyEvent())
            return;

        if (activityDone.getActivityInfo() == null)
            return;

        System.out.println("Received: ");

        ProcessInstance instance = instanceService
                .getProcessInstanceLocal(activityDone.getActivityInfo().getInstanceId());

        try {
            Activity activity = instance.getProcessDefinition(false)
                    .getActivity(activityDone.getActivityInfo().getTracingTag());

            if (activity instanceof ReceiveActivity) {
                ((ReceiveActivity) activity).fireReceived(instance, activityDone.getResult());
            }
            instance.execute(activityDone.getActivityInfo().getTracingTag());

            // broadcast to a separate topic to avoid loop with bpm-in/bpm-out
            eventPublisher.send("bpm-brodcast", activityDone);

        } catch (Exception e) {

            ActivityFailed activityFailed = new ActivityFailed();
            activityFailed.setActivityInfo(new ActivityInfo());
            activityFailed.getActivityInfo().setInstanceId(activityDone.getActivityInfo().getInstanceId());
            activityFailed.getActivityInfo().setTracingTag(activityDone.getActivityInfo().getTracingTag());

            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            activityFailed
                    .setMessage("[" + e.getClass().getName() + "]" + e.getMessage() + ":" + stringWriter.toString());

            eventPublisher.send("bpm-out", activityFailed);

            //// retry

            /**
             * retry
             * 
             * Activity activity = instance.getProcessDefinition()
             * .getActivity(activityFailed.getActivityInfo().getTracingTag());
             * if (activity.isQueuingEnabled()) {
             * 
             * int retryDelay = activity.getRetryDelay() > 0 ? activity.getRetryDelay() :
             * 30;
             * int retryLimit = activity.getRetryLimit() > 0 ? activity.getRetryLimit() : 5;
             * 
             * int currRetryCount = activity.getRetryCount(instance);
             * if (currRetryCount < retryLimit) {
             * Thread.sleep(retryDelay * 1000); /// fixme : changed to use Timer that tries
             * in different thread.
             * 
             * activityQueue.queue(instance.getInstanceId(), activity.getTracingTag(),
             * currRetryCount, null);
             * activity.setRetryCount(instance, currRetryCount + 1);
             * }
             * 
             * }
             * 
             */
        }

    }

    @ProcessTransactional
    public void handleFailed(ActivityFailed activityFailed) throws Exception {

        if (!activityFailed.checkMyEvent())
            return;

        try {
            if (activityFailed.getActivityInfo() == null) {
                return;
            }
            ProcessInstance instance = instanceService
                    .getProcessInstanceLocal(activityFailed.getActivityInfo().getInstanceId());

            instance.fireFault(activityFailed.getActivityInfo().getTracingTag(),
                    new UEngineException(activityFailed.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ProcessTransactional
    public void handleQueued(ActivityQueued activityQueued) throws Exception {

        if (!activityQueued.checkMyEvent())
            return;

        if (activityQueued.getActivityInfo() == null)
            return;

        ProcessInstance instance = instanceService
                .getProcessInstanceLocal(activityQueued.getActivityInfo().getInstanceId());

        try {
            instance.execute(activityQueued.getActivityInfo().getTracingTag());

            ActivityDone activityDone = new ActivityDone();
            activityDone.setActivityInfo(new ActivityInfo());
            activityDone.getActivityInfo().setInstanceId(activityQueued.getActivityInfo().getInstanceId());
            activityDone.getActivityInfo().setTracingTag(activityQueued.getActivityInfo().getTracingTag());

            eventPublisher.send("bpm-out", activityDone);

        } catch (Exception e) {

            ActivityFailed activityFailed = new ActivityFailed();
            activityFailed.setActivityInfo(new ActivityInfo());
            activityFailed.getActivityInfo().setInstanceId(activityQueued.getActivityInfo().getInstanceId());
            activityFailed.getActivityInfo().setTracingTag(activityQueued.getActivityInfo().getTracingTag());

            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            activityFailed
                    .setMessage("[" + e.getClass().getName() + "]" + e.getMessage() + ":" + stringWriter.toString());

            eventPublisher.send("bpm-out", activityFailed);

            // 예외를 다시 던져야 @ProcessTransactional 롤백이 수행되고, 인스턴스 파일(saveVariables)이 저장되지 않음.
            throw e;

            //// retry
            // Activity activity = instance.getProcessDefinition()
            // .getActivity(activityFailed.getActivityInfo().getTracingTag());
            // if (activity.isQueuingEnabled()) {

            // int retryDelay = activity.getRetryDelay() > 0 ? activity.getRetryDelay() :
            // 30;
            // int retryLimit = activity.getRetryLimit() > 0 ? activity.getRetryLimit() : 5;

            // int currRetryCount = activity.getRetryCount(instance);
            // if (currRetryCount < retryLimit) {
            // Thread.sleep(retryDelay * 1000); /// fixme : changed to use Timer that tries
            // in different thread.

            // activityQueue.queue(instance.getInstanceId(), activity.getTracingTag(),
            // currRetryCount, null);
            // activity.setRetryCount(instance, currRetryCount + 1);
            // }

            // }
        }

    }

    @ProcessTransactional
    public void handleDeployed(DefinitionDeployed definitionDeployed) {
        if (!definitionDeployed.checkMyEvent())
            return;

        String definitionPath = definitionDeployed.getDefintionId();

        if (definitionPath != null)
            try {
                definitionServiceUtil.getDefinition(definitionPath);
                // serviceRegisterDeployFilter.beforeDeploy((ProcessDefinition) definition,
                // null, definitionPath, true);
            } catch (Exception e) {
                throw new RuntimeException("failed to register a service for :" + definitionDeployed.getDefintionId(),
                        e);
            }

    }

    // @Autowired
    // ServiceRegisterDeployFilter serviceRegisterDeployFilter;

    @Autowired
    DefinitionServiceUtil definitionServiceUtil;

}
