package org.uengine.five.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.uengine.contexts.EventSynchronization;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.kernel.FieldDescriptor;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.bpmn.MessageStartEvent;
import org.uengine.kernel.bpmn.StartEvent;

class EventMappingDeployFilterTest {

    @Test
    void preservesSameEventAcrossDefinitionsAndRepeatsSyncIdempotently() throws Exception {
        Map<String, EventMappingEntity> stored = new HashMap<>();
        EventMappingRepository repository = repository(stored);

        EventMappingDeployFilter filter = new EventMappingDeployFilter();
        filter.eventMappingRepository = repository;

        filter.beforeDeploy(definition("definitions/loan-a.bpmn", "start-a"), null, "", false);
        filter.beforeDeploy(definition("definitions/loan-b.bpmn", "start-b"), null, "", false);
        filter.beforeDeploy(definition("definitions/loan-a.bpmn", "start-a"), null, "", false);

        assertEquals(2, stored.size());
        assertEquals(
                List.of("definitions/loan-a.bpmn", "definitions/loan-b.bpmn"),
                stored.values().stream()
                        .map(EventMappingEntity::getDefinitionId)
                        .sorted()
                        .toList());
    }

    @Test
    void registersEventConfiguredOnMessageStartEvent() throws Exception {
        Map<String, EventMappingEntity> stored = new HashMap<>();
        EventMappingDeployFilter filter = new EventMappingDeployFilter();
        filter.eventMappingRepository = repository(stored);

        MessageStartEvent startEvent = new MessageStartEvent();
        startEvent.setTracingTag("START_EVENT_01");
        startEvent.setEventKey("EVENT_REQUESTED");
        EventSynchronization synchronization = synchronization("");
        startEvent.setEventSynchronizations(new EventSynchronization[] {synchronization});
        ProcessDefinition definition = mock(ProcessDefinition.class);

        when(definition.getId()).thenReturn("KLI_FN00_M00.bpmn");
        when(definition.getStartActivities()).thenReturn(List.of(startEvent));
        when(definition.getEvents()).thenReturn(List.of(startEvent));
        when(definition.getChildActivities()).thenReturn(List.of(startEvent));

        filter.beforeDeploy(definition, null, "", false);

        assertEquals(1, stored.size());
        EventMappingEntity mapping = stored.values().iterator().next();
        assertEquals("EVENT_REQUESTED", mapping.getEventName());
        assertEquals("loanPcesMgmtNo", mapping.getCorrelationKey());
        assertTrue(mapping.isStartEvent());
    }

    @Test
    void keepsEventOnActivityAfterPlainStartAsNonStartEvent() throws Exception {
        Map<String, EventMappingEntity> stored = new HashMap<>();
        EventMappingDeployFilter filter = new EventMappingDeployFilter();
        filter.eventMappingRepository = repository(stored);

        StartEvent startEvent = mock(StartEvent.class);
        HumanActivity requestedWork = mock(HumanActivity.class);
        when(startEvent.getEventSynchronizations()).thenReturn(new EventSynchronization[0]);
        when(startEvent.getTracingTag()).thenReturn("START_EVENT_01");
        when(requestedWork.getTracingTag()).thenReturn("BPM_ACT_01");
        when(requestedWork.getEventSynchronizations())
                .thenReturn(new EventSynchronization[] {synchronization("EVENT_REQUESTED")});

        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("KLI_FN00_M00.bpmn");
        when(definition.getStartActivities()).thenReturn(List.of(startEvent));
        when(definition.getEvents()).thenReturn(List.of(startEvent));
        when(definition.getChildActivities()).thenReturn(List.of(startEvent, requestedWork));

        filter.beforeDeploy(definition, null, "", false);

        assertEquals(1, stored.size());
        assertFalse(stored.values().iterator().next().isStartEvent());
    }

    private static ProcessDefinition definition(String definitionId, String tracingTag) throws Exception {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        MessageStartEvent activity = new MessageStartEvent();
        activity.setTracingTag(tracingTag);
        activity.setEventKey("LOAN_RECEIVED");
        activity.setEventSynchronizations(new EventSynchronization[] {synchronization("")});

        when(definition.getId()).thenReturn(definitionId);
        when(definition.getStartActivities()).thenReturn(List.of(activity));
        when(definition.getEvents()).thenReturn(List.of(activity));
        when(definition.getChildActivities()).thenReturn(List.of(activity));
        return definition;
    }

    private static EventSynchronization synchronization(String eventType) {
        EventSynchronization synchronization = new EventSynchronization();
        synchronization.setEventType(eventType);
        FieldDescriptor correlation = new FieldDescriptor();
        correlation.setName("loanPcesMgmtNo");
        correlation.setIsCorrKey(true);
        synchronization.setAttributes(new FieldDescriptor[] {correlation});
        return synchronization;
    }

    private static EventMappingRepository repository(Map<String, EventMappingEntity> stored) {
        EventMappingRepository repository = mock(EventMappingRepository.class);
        when(repository.findByEventNameAndDefinitionIdAndTracingTagAndIsStartEvent(
                any(), any(), any(), any())).thenAnswer(invocation -> Optional.ofNullable(
                        stored.get(key(
                                invocation.getArgument(0),
                                invocation.getArgument(1),
                                invocation.getArgument(2),
                                invocation.getArgument(3)))));
        when(repository.save(any(EventMappingEntity.class))).thenAnswer(invocation -> {
            EventMappingEntity mapping = invocation.getArgument(0);
            stored.put(key(
                    mapping.getEventName(),
                    mapping.getDefinitionId(),
                    mapping.getTracingTag(),
                    mapping.isStartEvent()), mapping);
            return mapping;
        });
        return repository;
    }

    private static String key(Object event, Object definition, Object tracing, Object start) {
        return event + "|" + definition + "|" + tracing + "|" + start;
    }
}
