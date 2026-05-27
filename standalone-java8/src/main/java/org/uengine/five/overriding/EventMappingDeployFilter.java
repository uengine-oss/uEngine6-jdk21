package org.uengine.five.overriding;

import org.springframework.beans.factory.annotation.Autowired;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.five.service.InstanceService;
import org.uengine.contexts.EventSynchronization;
import org.uengine.kernel.Activity;
import org.uengine.kernel.DeployFilter;
import org.uengine.kernel.FieldDescriptor;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ReceiveActivity;
import org.uengine.kernel.UEngineException;
import org.uengine.kernel.bpmn.SequenceFlow;
import org.uengine.kernel.bpmn.StartEvent;
import org.uengine.processmanager.ProcessTransactionContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by uengine on 2018. 1. 5..
 */
public class EventMappingDeployFilter implements DeployFilter {

    @Autowired
    EventMappingRepository eventMappingRepository;

    @Override
    public void beforeDeploy(ProcessDefinition definition, ProcessTransactionContext tc, String path, boolean isNew) throws Exception {
       
        /* Condition (Find Start)
         * 1. EventSynchronization 존재 하는 첫번째 StartEvent
         * 2. ReceiveActivity 상속 Activity 
        */

        Set<Activity> startActivitiesWithEventSync = new HashSet<>();
        List<Activity> startActivities = definition.getStartActivities();
        if (startActivities != null) {
            for (Activity activity : startActivities) {
                Activity startActivity = findStartActivityWithEventSynchronization(activity, definition, new HashSet<>());
                if (startActivity == null) {
                    // Not Found Start Event
                } else {
                    startActivitiesWithEventSync.add(startActivity);
                    saveEventMappingEntity(startActivity, definition, true);
                }
            }
        }

        // ReceiveActivity && Except Start Activity
        List<Activity> activities = definition.getChildActivities();
        if (activities != null) {
            for (Activity activity : activities) {
                if (activity instanceof ReceiveActivity && !startActivitiesWithEventSync.contains(activity)
                        && activity.getEventSynchronizations().length > 0) {
                    saveEventMappingEntity(activity, definition, false);
                }
            }
        }

    }

    private Activity findStartActivityWithEventSynchronization(Activity activity, ProcessDefinition definition, Set<Activity> visited) throws Exception {
        try {
            if (activity == null) return null;
            if (visited != null && visited.contains(activity)) return null;
            if (visited != null) visited.add(activity);

            if ((activity instanceof StartEvent || activity instanceof ReceiveActivity)
                    && activity.getEventSynchronizations().length > 0) {
                return activity;
            }

            if (activity.getOutgoingSequenceFlows() != null) {
                for (SequenceFlow sequenceFlow : activity.getOutgoingSequenceFlows()) {
                    if (sequenceFlow.getTargetActivity() != null) {
                        Activity result = findStartActivityWithEventSynchronization(sequenceFlow.getTargetActivity(), definition, visited);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new UEngineException("Error when to find StartActivityWith EventSynchronization: " + activity.getName(), e);
        }
    }

    private void saveEventMappingEntity(Activity activity, ProcessDefinition definition, boolean isStartEvent) throws Exception {
        try {
            if (activity == null) return;
            EventSynchronization[] syncs = activity.getEventSynchronizations();
            if (syncs == null || syncs.length == 0) return;

            for (EventSynchronization sync : syncs) {
                if (sync == null) continue;

                String corrKey = null;
                FieldDescriptor[] attributes = sync.getAttributes();
                if (attributes == null) attributes = new FieldDescriptor[0];
                FieldDescriptor[] corrKeyFields = Arrays.stream(attributes).filter(FieldDescriptor::getIsCorrKey).toArray(FieldDescriptor[]::new);
                if (corrKeyFields.length > 0) {
                    corrKey = corrKeyFields[0].getName();
                }

                String eventType = sync.getEventType();
                if (eventType == null || eventType.trim().isEmpty()) continue;
                eventType = eventType.trim();

                // event_name(UNIQUE) 기준 find-or-update — 재배포 시 멱등 upsert
                EventMappingEntity eventMappingEntity = eventMappingRepository.findByEventName(eventType);
                if (eventMappingEntity == null) {
                    eventMappingEntity = new EventMappingEntity();
                    eventMappingEntity.setEventName(eventType);
                }
                eventMappingEntity.setDefinitionId(definition.getId());
                eventMappingEntity.setCorrelationKey(corrKey);
                eventMappingEntity.setTracingTag(activity.getTracingTag());
                eventMappingEntity.setIsStartEvent(isStartEvent);

                eventMappingRepository.save(eventMappingEntity);
            }
        } catch (Exception e) {
            throw new UEngineException("Error when to save EventMappingEntity: " + activity.getName(), e);
        }
    }
}
