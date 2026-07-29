package org.uengine.five.overriding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.contexts.EventSynchronization;
import org.uengine.kernel.Activity;
import org.uengine.kernel.DeployFilter;
import org.uengine.kernel.FieldDescriptor;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ReceiveActivity;
import org.uengine.kernel.UEngineException;
import org.uengine.kernel.bpmn.CatchingRestMessageEvent;
import org.uengine.kernel.bpmn.Event;
import org.uengine.processmanager.ProcessTransactionContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by uengine on 2018. 1. 5..
 */
public class EventMappingDeployFilter implements DeployFilter {

    private static final Logger log = LoggerFactory.getLogger(EventMappingDeployFilter.class);

    @Autowired
    EventMappingRepository eventMappingRepository;

    @Override
    public void beforeDeploy(ProcessDefinition definition, ProcessTransactionContext tc, String path, boolean isNew)
            throws Exception {

        /*
         * Condition (Find Start)
         * 1. EventSynchronization 존재 하는 첫번째 StartEvent
         * 2. ReceiveActivity 상속 Activity
         */

        Set<String> processStartEventTracingTags = new HashSet<>();
        List<Activity> startActivities = definition.getStartActivities();
        if (startActivities != null) {
            for (Activity activity : startActivities) {
                if (activity instanceof Event && activity.getTracingTag() != null) {
                    processStartEventTracingTags.add(activity.getTracingTag());
                }
            }
        }

        List<Activity> startEvents = definition.getEvents();
        if (startEvents != null) {
            for (Activity startEvent : startEvents) {
                boolean isProcessStartEvent =
                        processStartEventTracingTags.contains(startEvent.getTracingTag());
                saveEventMappingEntity(true, startEvent, definition, isProcessStartEvent);
            }
        }

        // Event nodes are handled above. Event synchronizations on normal activities
        // are receive mappings even when the activity directly follows a plain start node.
        List<Activity> activities = definition.getChildActivities();
        if (activities != null) {
            for (Activity activity : activities) {
                if (activity instanceof ReceiveActivity
                        && !(activity instanceof Event)
                        && activity.getEventSynchronizations().length > 0) {
                    saveEventMappingEntity(activity, definition, false);
                }
            }
        }

    }

    private void saveEventMappingEntity(boolean isEvent, Activity activity, ProcessDefinition definition,
            boolean isStartEvent)
            throws Exception {
        if (isEvent) {
            if (activity instanceof Event) {
                Event event = (Event) activity;
                String eventKey = event.getEventKey();
                if (Event.THROW_EVENT.equals(event.getEventType()))
                    return;

                // An event key is required for a stable mapping identity.
                if (isNullOrBlank(eventKey)) {
                    log.warn("Skip EventMappingEntity save: eventKey is null/blank. defId={}, tracingTag={}",
                            safe(definition != null ? definition.getId() : null),
                            safe(activity.getTracingTag()));
                    return;
                }
                eventKey = eventKey.trim();

                // Upsert by the full mapping target; event names may be shared.
                EventMappingEntity eventMappingEntity = findOrCreate(
                        eventKey, definition, activity, isStartEvent);
                eventMappingEntity.setCorrelationKey(resolveCorrelationKey(activity));
                eventMappingEntity.setIsStartEvent(isStartEvent);

                eventMappingRepository.save(eventMappingEntity);
                logRegisteredEventMapping("bpmn-event", eventMappingEntity);
            }
        } else {
            saveEventMappingEntity(activity, definition, isStartEvent);
        }

    }

    private void saveEventMappingEntity(Activity activity, ProcessDefinition definition, boolean isStartEvent)
            throws Exception {
        try {
            if (activity == null) return;
            EventSynchronization[] syncs = activity.getEventSynchronizations();
            if (syncs == null || syncs.length == 0) return;
            if (eventMappingRepository == null) {
                throw new IllegalStateException("eventMappingRepository is null. EventMappingDeployFilter might not be Spring-managed.");
            }

            for (EventSynchronization sync : syncs) {
                if (sync == null) continue;

                String corrKey = null;
                FieldDescriptor[] attributes = sync.getAttributes();
                if (attributes == null) attributes = new FieldDescriptor[0];
                FieldDescriptor[] corrKeyFields = Arrays.stream(attributes).filter(FieldDescriptor::getIsCorrKey)
                        .toArray(FieldDescriptor[]::new);
                if (corrKeyFields.length > 0) {
                    corrKey = corrKeyFields[0].getName();
                }

                String eventType = sync.getEventType();
                if (isNullOrBlank(eventType)) {
                    continue;
                }
                eventType = eventType.trim();

                // Upsert by the full mapping target; event names may be shared.
                EventMappingEntity eventMappingEntity = findOrCreate(
                        eventType, definition, activity, isStartEvent);
                eventMappingEntity.setCorrelationKey(corrKey);
                eventMappingEntity.setIsStartEvent(isStartEvent);

                eventMappingRepository.save(eventMappingEntity);
                logRegisteredEventMapping("event-sync", eventMappingEntity);
            }
        } catch (Exception e) {
            throw new UEngineException("Error when to save EventMappingEntity: " + activity.getName(), e);
        }
    }

    private EventMappingEntity findOrCreate(
            String eventName,
            ProcessDefinition definition,
            Activity activity,
            Boolean isStartEvent) {
        String definitionId = definition.getId();
        String tracingTag = activity.getTracingTag();
        EventMappingEntity mapping = eventMappingRepository
                .findByEventNameAndDefinitionIdAndTracingTagAndIsStartEvent(
                        eventName, definitionId, tracingTag, isStartEvent)
                .orElseGet(EventMappingEntity::new);
        mapping.setEventName(eventName);
        mapping.setDefinitionId(definitionId);
        mapping.setTracingTag(tracingTag);
        mapping.setIsStartEvent(isStartEvent);
        return mapping;
    }

    private String resolveCorrelationKey(Activity activity) {
        EventSynchronization[] synchronizations = activity.getEventSynchronizations();
        if (synchronizations != null) {
            for (EventSynchronization synchronization : synchronizations) {
                if (synchronization == null || synchronization.getAttributes() == null) {
                    continue;
                }
                for (FieldDescriptor attribute : synchronization.getAttributes()) {
                    if (attribute != null && attribute.getIsCorrKey()) {
                        return attribute.getName();
                    }
                }
            }
        }
        if (activity instanceof CatchingRestMessageEvent) {
            return ((CatchingRestMessageEvent) activity).getCorrelationKey();
        }
        return null;
    }

    private void logRegisteredEventMapping(String source, EventMappingEntity eventMappingEntity) {
        if (eventMappingEntity == null) {
            return;
        }
        log.info(
                "Registered event mapping: source={}, definitionId={}, eventName={}, correlationKey={}, tracingTag={}, isStartEvent={}",
                safe(source),
                safe(eventMappingEntity.getDefinitionId()),
                safe(eventMappingEntity.getEventName()),
                safe(eventMappingEntity.getCorrelationKey()),
                safe(eventMappingEntity.getTracingTag()),
                eventMappingEntity.isStartEvent());
    }

    private static boolean isNullOrBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String safe(String v) {
        return v == null ? "(null)" : v;
    }
}
