package org.uengine.five.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.contexts.EventSynchronization;
import org.uengine.five.dto.ProcessExecutionCommand;
import org.uengine.five.dto.ProcessVariableValue;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactional;
import org.uengine.five.messaging.NonRetryableInboxException;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.kernel.Activity;
import org.uengine.kernel.DefaultProcessInstance;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ReceiveActivity;
import org.uengine.util.UEngineUtil;
import org.uengine.kernel.bpmn.Event;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AsyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventListener.class);
    private static final Pattern NUMERIC_CSV = Pattern.compile("^\\s*\\d+(\\s*,\\s*\\d+)*\\s*$");

    @Autowired
    ProcessInstanceRepository processInstanceRepository;

    @Autowired
    InstanceService instanceService;

    @Autowired
    InstanceServiceImpl instanceServiceImpl;

    @Autowired
    DefinitionServiceUtil definitionService;

    @Autowired
    EventMappingRepository eventMappingRepository;

    @Autowired
    WorklistRepository worklistRepository;

    public void whatever(String eventString) {
        System.out.println("\n\n##### listener whatever : " + eventString + "\n\n");
    }

    @Transactional(rollbackFor = { Exception.class })
    @ProcessTransactional
    public void wheneverEvent(String eventBody, String typeHeader, String inboxCorrKey) {
        wheneverEvent(eventBody, typeHeader, inboxCorrKey, null);
    }

    @Transactional(rollbackFor = { Exception.class })
    @ProcessTransactional
    public void wheneverEvent(
            String eventBody,
            String typeHeader,
            String inboxCorrKey,
            String actorEndpoint) {
        log.info("[BPM] wheneverEvent called, typeHeader={}, inboxCorrKey={}", typeHeader, inboxCorrKey);
        try {
            String eventType = typeHeader;
            HashMap<String, Object> eventContent = eventObjectMapper().readValue(eventBody, HashMap.class);
            List<EventMappingEntity> eventMappings =
                    eventMappingRepository.findAllByEventNameOrderByIdAsc(eventType);

            if (eventMappings.isEmpty()) {
                String decoded = decodeNumericCsvIfNeeded(typeHeader);
                if (!decoded.equals(typeHeader)) {
                    eventType = decoded;
                    eventMappings = eventMappingRepository.findAllByEventNameOrderByIdAsc(eventType);
                }
            }

            if (eventMappings.isEmpty()) {
                log.error("[BPM] EventMapping not found for eventType='{}'", eventType);
                throw new IllegalStateException("EventMappingEntity is null for eventType: " + eventType);
            }

            processEventMappings(eventMappings, eventType, eventContent, inboxCorrKey, actorEndpoint);
        } catch (Exception e) {
            throw new RuntimeException("Error wheneverEvent :" + e.getMessage(), e);
        }
    }

    private void processEventMappings(
            List<EventMappingEntity> eventMappings,
            String eventType,
            HashMap<String, Object> eventContent,
            String inboxCorrKey,
            String actorEndpoint) throws Exception {
        Set<String> receiveCorrelationValues = new LinkedHashSet<>();
        Set<String> startedDefinitions = new LinkedHashSet<>();
        int startFailures = 0;
        boolean shouldTriggerWaitingEvents = false;

        for (EventMappingEntity mapping : eventMappings) {
            String correlationField = mapping.getCorrelationKey();
            Object payloadCorrelation = correlationField == null ? null : eventContent.get(correlationField);
            String correlationValue = payloadCorrelation != null
                    ? payloadCorrelation.toString()
                    : firstNonBlank(inboxCorrKey, correlationField);

            if (Boolean.TRUE.equals(mapping.isStartEvent())
                    && startedDefinitions.add(mapping.getDefinitionId())) {
                try {
                    if (!hasRunningMappedDefinition(mapping, correlationValue)) {
                        startMappedDefinition(mapping, correlationValue, eventContent);
                    }
                } catch (Exception e) {
                    startFailures++;
                    log.error("[BPM] Failed to start mapped definition: eventType={}, definitionId={}",
                            eventType, mapping.getDefinitionId(), e);
                }
            }

            if (correlationValue != null) {
                receiveCorrelationValues.add(correlationValue);
            }
            if (!Boolean.TRUE.equals(mapping.isStartEvent()) && payloadCorrelation == null) {
                shouldTriggerWaitingEvents = true;
            }
        }

        for (String correlationValue : receiveCorrelationValues) {
            triggerReceiveActivitiesByCorrKeyAndEventType(
                    correlationValue, eventType, eventContent, actorEndpoint);
        }

        if (!startedDefinitions.isEmpty() && startFailures == startedDefinitions.size()) {
            throw new IllegalStateException("All mapped process starts failed for eventType: " + eventType);
        }

        if (shouldTriggerWaitingEvents) {
            triggerWaitingEvents(eventType);
        }
    }

    private void startMappedDefinition(
            EventMappingEntity mapping,
            String correlationValue,
            HashMap<String, Object> eventContent) throws Exception {
        ProcessExecutionCommand command = new ProcessExecutionCommand();
        command.setProcessDefinitionId(mapping.getDefinitionId());
        command.setCorrelationKeyValue(correlationValue);
        command.setProcessVariableValues(toProcessVariables(eventContent, mapping.getCorrelationKey()));
        command.setStartEventPayload(eventContent);
        instanceService.start(command);
    }

    boolean hasRunningMappedDefinition(EventMappingEntity mapping, String correlationValue) {
        if (!UEngineUtil.isNotEmpty(correlationValue)) {
            return false;
        }
        String mappedDefinition = withoutBpmnExtension(mapping.getDefinitionId());
        return processInstanceRepository.findByCorrKeyAndStatus(correlationValue, "Running").stream()
                .map(ProcessInstanceEntity::getDefId)
                .map(AsyncEventListener::withoutBpmnExtension)
                .anyMatch(mappedDefinition::equals);
    }

    private static String withoutBpmnExtension(String definitionId) {
        if (definitionId == null) {
            return "";
        }
        return definitionId.endsWith(".bpmn")
                ? definitionId.substring(0, definitionId.length() - ".bpmn".length())
                : definitionId;
    }

    private void triggerReceiveActivitiesByCorrKeyAndEventType(
            String correlationValue,
            String eventType,
            HashMap<String, Object> eventContent,
            String actorEndpoint) throws Exception {
        List<ProcessInstanceEntity> processInstances =
                processInstanceRepository.findByCorrKeyAndStatus(correlationValue, "Running");
        for (ProcessInstanceEntity processInstanceEntity : processInstances) {
            ProcessInstance instance = instanceServiceImpl
                    .getProcessInstanceLocal(processInstanceEntity.getInstId().toString());

            applyEventValues(instance, eventContent);

            boolean received = false;
            activityLoop:
            for (Activity activity : instance.getCurrentRunningActivities()) {
                for (EventSynchronization sync : activity.getEventSynchronizations()) {
                    if (sync != null && eventType.equals(sync.getEventType())) {
                        String previousUserId = GlobalContext.getUserId();
                        try {
                            validateHumanActivityCompletion(instance, activity, actorEndpoint);
                            ((DefaultProcessInstance) instance).set(
                                    activity.getTracingTag(),
                                    DefaultProcessInstance.EVENT_DATA,
                                    (Serializable) eventContent);
                            ((ReceiveActivity) activity).fireReceived(instance, eventContent);
                        } finally {
                            GlobalContext.setUserId(previousUserId);
                        }
                        received = true;
                        break activityLoop;
                    }
                }
            }
        }
    }

    void validateHumanActivityCompletion(
            ProcessInstance instance,
            Activity activity,
            String actorEndpoint) throws Exception {
        if (!(activity instanceof HumanActivity)) {
            return;
        }
        if (!UEngineUtil.isNotEmpty(actorEndpoint)) {
            throw new NonRetryableInboxException("header.emnb is required to complete a work item");
        }

        HumanActivity humanActivity = (HumanActivity) activity;
        String[] taskIds = humanActivity.getTaskIds(instance);
        WorklistEntity owned = null;
        boolean unclaimed = false;
        if (taskIds != null) {
            for (String taskId : taskIds) {
                if (!UEngineUtil.isNotEmpty(taskId)) {
                    continue;
                }
                WorklistEntity worklist = worklistRepository.findByIdForUpdate(Long.valueOf(taskId)).orElse(null);
                if (worklist == null) {
                    continue;
                }
                if (!UEngineUtil.isNotEmpty(worklist.getEndpoint())) {
                    unclaimed = true;
                } else if (worklist.getEndpoint().equals(actorEndpoint.trim())) {
                    owned = worklist;
                    break;
                }
            }
        }

        if (owned == null) {
            throw new NonRetryableInboxException(unclaimed
                    ? "Work item must be claimed before completion"
                    : "Only the current work item owner can complete this task");
        }

        instance.setProperty(activity.getTracingTag(), HumanActivity.PVKEY_TASKID,
                String.valueOf(owned.getTaskId()));
        GlobalContext.setUserId(actorEndpoint.trim());
    }

    private void triggerWaitingEvents(String eventType) throws Exception {
        List<ProcessInstanceEntity> processInstances = processInstanceRepository.findByStatus("Running");
        for (ProcessInstanceEntity processInstanceEntity : processInstances) {
            ProcessInstance instance = instanceServiceImpl
                    .getProcessInstanceLocal(processInstanceEntity.getInstId().toString());
            for (Activity activity : instance.getCurrentRunningActivities()) {
                if (activity instanceof Event) {
                    Event event = (Event) activity;
                    if (eventType.equals(event.getEventKey())
                            && !Event.THROW_EVENT.equals(event.getEventType())) {
                        event.onMessage(instance, event.getTracingTag());
                    }
                    break;
                }
            }
        }
    }

    static ProcessVariableValue[] toProcessVariables(
            HashMap<String, Object> eventContent,
            String correlationKey) {
        List<ProcessVariableValue> variables = new ArrayList<>();
        for (Map.Entry<String, Object> entry : eventContent.entrySet()) {
            if (entry.getKey().equals(correlationKey)
                    || entry.getValue() == null
                    || !(entry.getValue() instanceof Serializable)) {
                continue;
            }

            ProcessVariableValue variable = new ProcessVariableValue();
            variable.setName(entry.getKey());
            variable.setValues(new Serializable[] {(Serializable) entry.getValue()});
            variables.add(variable);
        }
        return variables.toArray(new ProcessVariableValue[0]);
    }

    private static void applyEventValues(ProcessInstance instance, Map<String, Object> eventContent) throws Exception {
        for (Map.Entry<String, Object> entry : eventContent.entrySet()) {
            if (entry.getKey() != null && entry.getValue() instanceof Serializable) {
                instance.set("", entry.getKey(), (Serializable) entry.getValue());
            }
        }
    }

    private static ObjectMapper eventObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibilityChecker(mapper.getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        return mapper;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static String decodeNumericCsvIfNeeded(String raw) {
        try {
            if (raw == null) {
                return "";
            }

            String trimmed = raw.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
            if (!NUMERIC_CSV.matcher(trimmed).matches()) {
                return raw;
            }

            String[] parts = trimmed.split("\\s*,\\s*");
            byte[] bytes = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                bytes[i] = (byte) Integer.parseInt(parts[i]);
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
