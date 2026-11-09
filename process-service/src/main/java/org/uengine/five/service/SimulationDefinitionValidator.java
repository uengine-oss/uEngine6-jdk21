package org.uengine.five.service;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.EventSynchronization;
import org.uengine.five.overriding.InstanceDataAppendingActivityFilter;
import org.uengine.five.overriding.InstanceNameFilter;
import org.uengine.five.overriding.PayloadFilter;
import org.uengine.kernel.*;
import org.uengine.kernel.bpmn.EndEvent;
import org.uengine.kernel.bpmn.BusinessRuleTask;
import org.uengine.kernel.bpmn.ExclusiveGateway;
import org.uengine.kernel.bpmn.FlowActivity;
import org.uengine.kernel.bpmn.Gateway;
import org.uengine.kernel.bpmn.InclusiveGateway;
import org.uengine.kernel.bpmn.ParallelGateway;
import org.uengine.kernel.bpmn.SequenceFlow;
import org.uengine.kernel.bpmn.StartEvent;
import org.uengine.kernel.bpmn.SubProcess;
import org.uengine.processdesigner.mapper.Transformer;
import org.uengine.processdesigner.mapper.TransformerMapping;
import org.uengine.processdesigner.mapper.transformers.DirectValueTransformer;
import org.uengine.processdesigner.mapper.transformers.JsonBuildTransformer;
import org.uengine.processdesigner.mapper.transformers.JsonParseTransformer;

/** The supported interactive simulation subset; not a sandbox for arbitrary BPMN code. */
final class SimulationDefinitionValidator {
    private static final Set<Class<?>> ACTIVITIES = Set.of(ProcessDefinition.class, HumanActivity.class,
            FormActivity.class, URLActivity.class, DefaultActivity.class, StartEvent.class, EndEvent.class,
            Gateway.class, ExclusiveGateway.class, ParallelGateway.class, InclusiveGateway.class, SubProcess.class,
            BusinessRuleTask.class);
    private static final Set<Class<?>> FILTERS = Set.of(InstanceDataAppendingActivityFilter.class,
            InstanceNameFilter.class, PayloadFilter.class);
    private static final Set<Class<?>> TRANSFORMERS = Set.of(DirectValueTransformer.class,
            JsonBuildTransformer.class, JsonParseTransformer.class);
    private static final Pattern TEMPLATE = Pattern.compile("<%=([^%]+)%>|\\{([^{}]+)\\}");
    private static final Pattern UNSAFE_PATH = Pattern.compile("[(){};\\r\\n]|(?i)(^|\\.)(class|classLoader|processDefinition|processInstanceEntity)(\\.|$)");
    private static final Pattern INSTANCE_PATH = Pattern.compile("\\[instance\\]\\.(name|info|ext[1-5])");
    private static final Pattern ROLE_PATH = Pattern.compile("\\[(roles|lanes)\\]\\.[^.\\[\\]]+\\.(endpoint|groupName|scope)");

    private SimulationDefinitionValidator() { }

    private Consumer<BusinessRuleTask> businessRules;
    private boolean hasBusinessRule;

    static void validate(ProcessDefinition definition) {
        validate(definition, null);
    }

    static void validate(ProcessDefinition definition, Consumer<BusinessRuleTask> businessRules) {
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시뮬레이션할 프로세스 정의가 없습니다.");
        }
        SimulationDefinitionValidator check = new SimulationDefinitionValidator();
        check.businessRules = businessRules;
        if (definition.getClass() != ProcessDefinition.class) {
            check.reject(definition, "지원하지 않는 프로세스 정의 유형");
        }
        check.template(definition, definition.getInstanceNamePattern());
        ActivityFilter[] filters = definition.getActivityFilters();
        if (filters != null) {
            for (ActivityFilter filter : filters) {
                if (filter == null || !FILTERS.contains(filter.getClass())) {
                    check.reject(definition, "지원하지 않는 활동 필터");
                }
            }
        }
        Set<Activity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        check.activity(definition, visited);
        if (check.hasBusinessRule) {
            ProcessVariable[] variables = definition.getProcessVariables();
            if (variables != null) for (ProcessVariable variable : variables) {
                if (variable == null) check.reject(definition, "DMN 선언 변수가 없습니다");
                check.variablePath(definition, variable.getName());
            }
            for (Activity activity : visited) {
                if (activity.isQueuingEnabled() || activity.isFaultTolerant()) {
                    check.reject(activity, "DMN 시험은 큐 실행과 실패 무시를 지원하지 않습니다");
                }
            }
        }
    }

    private void activity(Activity activity, Set<Activity> visited) {
        if (activity == null || !visited.add(activity)) return;
        if (!ACTIVITIES.contains(activity.getClass())) reject(activity, "실제 외부 호출 또는 미지원 활동은 시험 실행할 수 없습니다");
        if (activity.getEventHandlers() != null && activity.getEventHandlers().length > 0) {
            reject(activity, "이벤트 핸들러는 시험 실행을 지원하지 않습니다");
        }
        if (activity.getClass() == BusinessRuleTask.class) {
            hasBusinessRule = true;
            BusinessRuleTask task = (BusinessRuleTask) activity;
            if (businessRules == null) reject(activity, "DMN 룰 사전검사가 필요합니다");
            String ruleId = task.getBusinessRuleId();
            if (ruleId == null || !ruleId.matches("[\\p{L}\\p{N}_-]{1,128}")) {
                reject(activity, "지원하지 않는 DMN 룰 ID");
            }
            businessRules.accept(task);
        }
        EventSynchronization[] synchronizations = activity.getEventSynchronizations();
        if (synchronizations != null) {
            for (EventSynchronization synchronization : synchronizations) {
                if (synchronization != null && synchronization.getMappingContext() != null) {
                    if (activity.getClass() == BusinessRuleTask.class) {
                        businessRuleParameters((BusinessRuleTask) activity,
                                synchronization.getMappingContext().getMappingElements());
                    } else parameters(activity, synchronization.getMappingContext().getMappingElements());
                }
            }
        }
        if (activity instanceof ReceiveActivity receive) parameters(activity, receive.getParameters());
        if (activity instanceof HumanActivity human) {
            template(activity, human.getTool());
            if (human.getInput() != null) path(activity, human.getInput().getName());
            if (human.getInstruction() != null) template(activity, human.getInstruction().getText());
        }
        if (activity instanceof FormActivity form && form.getVariableForHtmlFormContext() != null) {
            path(activity, form.getVariableForHtmlFormContext().getName());
        }
        if (activity instanceof URLActivity url) {
            template(activity, url.getUrl());
            template(activity, url.getUrlWhenSuccess());
            if (url.getResultURLPV() != null) path(activity, url.getResultURLPV().getName());
            if (url.getResultHTMLPV() != null) path(activity, url.getResultHTMLPV().getName());
            parameters(activity, url.getMappingFromResultHTML());
        }
        if (activity instanceof FlowActivity flow) {
            for (Activity child : flow.getChildActivities()) activity(child, visited);
            if (flow.getSequenceFlows() != null) {
                for (SequenceFlow sequence : flow.getSequenceFlows()) sequence(activity, sequence, visited);
            }
        }
        if (activity.getOutgoingSequenceFlows() != null) {
            for (SequenceFlow sequence : activity.getOutgoingSequenceFlows()) sequence(activity, sequence, visited);
        }
    }

    private void sequence(Activity owner, SequenceFlow sequence, Set<Activity> visited) {
        if (sequence == null || sequence.getClass() != SequenceFlow.class) reject(owner, "미지원 시퀀스 흐름");
        condition(owner, sequence.getCondition(), Collections.newSetFromMap(new IdentityHashMap<>()));
        activity(sequence.getTargetActivity(), visited);
    }

    private void condition(Activity owner, Condition condition, Set<Condition> visited) {
        if (condition == null) return;
        if (!visited.add(condition)) reject(owner, "순환 조건식");
        Class<?> type = condition.getClass();
        if (type == And.class || type == Or.class) {
            Condition[] children = ((And) condition).getConditions();
            if (children != null) for (Condition child : children) condition(owner, child, visited);
        } else if (type == Not.class) {
            condition(owner, ((Not) condition).getCondition(), visited);
        } else if (type == Evaluate.class) {
            Evaluate evaluate = (Evaluate) condition;
            if (evaluate.getKey() != null) path(owner, evaluate.getKey());
            Object value = evaluate.getValue();
            if (value instanceof String string) {
                if ("variable".equalsIgnoreCase(evaluate.getType())) path(owner, string);
                else template(owner, string);
            } else if (value instanceof ProcessVariable variable) {
                path(owner, variable.getName());
            } else json(owner, value, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        } else if (type != Otherwise.class) reject(owner, "스크립트 또는 미지원 조건식");
        visited.remove(condition);
    }

    private void parameters(Activity owner, ParameterContext[] parameters) {
        if (parameters == null) return;
        for (ParameterContext parameter : parameters) {
            if (parameter == null) reject(owner, "빈 매핑 요소");
            if (parameter.getArgument() != null && parameter.getArgument().getText() != null) {
                path(owner, parameter.getArgument().getText());
            }
            if (parameter.getVariable() != null) path(owner, parameter.getVariable().getName());
            if (parameter.getTransformerMapping() != null) {
                transformer(owner, parameter.getTransformerMapping(), Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
    }

    private void businessRuleParameters(BusinessRuleTask task, ParameterContext[] parameters) {
        if (parameters == null) return;
        String prefix = "[businessRule].r_" + task.getBusinessRuleId();
        for (ParameterContext parameter : parameters) {
            if (parameter == null || parameter.getArgument() == null || parameter.getVariable() == null
                    || parameter.getTransformerMapping() != null) reject(task, "지원하지 않는 DMN 매핑");
            String argument = parameter.getArgument().getText();
            String variable = parameter.getVariable().getName();
            boolean input = argument != null && argument.startsWith(prefix + ".in_")
                    && argument.length() > prefix.length() + 4;
            boolean output = variable != null && variable.startsWith(prefix + ".out_")
                    && variable.length() > prefix.length() + 5;
            if (input == output) reject(task, "DMN 입력/출력과 룰 ID가 일치하지 않습니다");
            variablePath(task, input ? variable : argument);
            if (output && argument.contains(".in_")) reject(task, "DMN 출력 변수의 .in_ 경로는 지원하지 않습니다");
            String key = input ? argument.substring(prefix.length() + 4) : variable.substring(prefix.length() + 5);
            variablePath(task, key);
            String direction = parameter.getDirection();
            if (direction != null && !direction.isBlank()
                    && !(input ? "in" : "out").equalsIgnoreCase(direction)) {
                reject(task, "DMN 매핑 방향이 일치하지 않습니다");
            }
        }
    }

    private void variablePath(Activity owner, String variable) {
        path(owner, variable);
        if (variable.contains("[") || variable.contains("]")) reject(owner, "DMN은 일반 변수 경로만 지원합니다");
    }

    private void transformer(Activity owner, TransformerMapping mapping, Set<Transformer> visited) {
        Transformer transformer = mapping.getTransformer();
        if (transformer == null || !TRANSFORMERS.contains(transformer.getClass())) reject(owner, "미지원 매핑 변환기");
        if (!visited.add(transformer)) reject(owner, "순환 매핑 변환기");
        if (transformer instanceof DirectValueTransformer direct) {
            json(owner, direct.getValue(), Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        }
        if (transformer.getArgumentSourceMap() != null) {
            for (Object source : transformer.getArgumentSourceMap().values()) transformerSource(owner, source, visited);
        }
        visited.remove(transformer);
    }

    private void transformerSource(Activity owner, Object source, Set<Transformer> visited) {
        if (source == null) return;
        if (source instanceof TransformerMapping mapping) transformer(owner, mapping, visited);
        else if (source instanceof String string) {
            if (!string.startsWith("[direct].")) path(owner, string);
        } else if (source instanceof List<?> sources) {
            for (Object item : sources) transformerSource(owner, item, visited);
        } else reject(owner, "미지원 변환기 입력");
    }

    private void json(Activity owner, Object value, Set<Object> visited, int depth) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return;
        if (depth > 30 || !visited.add(value)) reject(owner, "JSON이 아닌 매핑 상수");
        if (value instanceof List<?> list) {
            for (Object item : list) json(owner, item, visited, depth + 1);
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) reject(owner, "문자열이 아닌 JSON 키");
                json(owner, entry.getValue(), visited, depth + 1);
            }
        } else reject(owner, "JSON이 아닌 매핑 상수");
        visited.remove(value);
    }

    private void template(Activity owner, String text) {
        if (text == null) return;
        Matcher matcher = TEMPLATE.matcher(text);
        StringBuffer remainder = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            path = path.trim();
            if (path.startsWith("instance.")) path = "[instance]." + path.substring("instance.".length());
            if (path.matches("(activity|definition|systemEnvironments|systemProperties)(\\..*)?")) {
                reject(owner, "지원하지 않는 표현식 객체");
            }
            path(owner, path);
            matcher.appendReplacement(remainder, "");
        }
        matcher.appendTail(remainder);
        if (remainder.indexOf("{") >= 0 || remainder.indexOf("}") >= 0
                || remainder.indexOf("<%") >= 0 || remainder.indexOf("%>") >= 0) {
            reject(owner, "지원하지 않는 실행 표현식");
        }
    }

    private void path(Activity owner, String path) {
        if (path == null || path.isBlank() || UNSAFE_PATH.matcher(path).find()) reject(owner, "지원하지 않는 매핑 경로");
        String candidate = path.trim();
        if (candidate.startsWith("[instance].")) {
            if (!INSTANCE_PATH.matcher(candidate).matches()) reject(owner, "지원하지 않는 인스턴스 속성 경로");
        } else if (candidate.startsWith("[roles].") || candidate.startsWith("[lanes].")) {
            if (!ROLE_PATH.matcher(candidate).matches()) reject(owner, "지원하지 않는 역할 속성 경로");
        } else {
            if (candidate.startsWith("[Arguments].")) candidate = candidate.substring("[Arguments].".length());
            if (candidate.isBlank() || candidate.contains("[") || candidate.contains("]") || candidate.startsWith(".")
                    || candidate.endsWith(".") || candidate.contains("..")) reject(owner, "지원하지 않는 변수 경로");
        }
    }

    private void reject(Activity activity, String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "시뮬레이션 지원 제한: " + reason + " (" + activity.getTracingTag() + ", "
                        + activity.getClass().getSimpleName() + ")");
    }
}
