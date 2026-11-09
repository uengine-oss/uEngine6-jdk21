package org.uengine.kernel.test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import junit.framework.TestCase;

import org.uengine.contexts.EventSynchronization;
import org.uengine.contexts.MappingContext;
import org.uengine.contexts.TextContext;
import org.uengine.kernel.AbstractProcessInstance;
import org.uengine.kernel.DefaultActivity;
import org.uengine.kernel.DefaultComponentFactory;
import org.uengine.kernel.DefaultProcessInstance;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.IComponentFactory;
import org.uengine.kernel.MappingElement;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ProcessVariable;
import org.uengine.kernel.bpmn.BusinessRuleRuntime;
import org.uengine.kernel.bpmn.BusinessRuleTask;
import org.uengine.kernel.bpmn.SequenceFlow;
import org.uengine.kernel.bpmn.StartEvent;

/**
 * {@link BusinessRuleTask#executeActivity(ProcessInstance)}가 {@link BusinessRuleRuntime}
 * 빈이 등록된 경우 DMN 평가와 완료까지 동기적으로 수행함을 검증한다.
 *
 * 배경: 이전에는 {@code BusinessRuleTaskAutoEvaluatorFilter}가 ActivityFilter#afterExecute
 * 훅으로 평가를 시도했지만, BusinessRuleTask가 ReceiveActivity를 상속하므로
 * 외부 메시지가 도착하기 전에는 afterExecute가 절대 호출되지 않아 필터가 사실상 동작하지
 * 않았다. 지금은 executeActivity 자체에서 SPI를 조회해 평가하므로 단일 실행 스텝 안에
 * 완료된다.
 */
public class BusinessRuleTaskTest extends TestCase {

    private ProcessDefinition processDefinition;
    private BusinessRuleTask businessRuleTask;
    private IComponentFactory originalFactory;
    private Class originalInstanceClass;

    public void setUp() throws Exception {
        originalInstanceClass = AbstractProcessInstance.USE_CLASS;
        AbstractProcessInstance.USE_CLASS = DefaultProcessInstance.class;

        processDefinition = new ProcessDefinition();
        processDefinition.setProcessVariables(new ProcessVariable[] {
                ProcessVariable.forName("신용도"),
                ProcessVariable.forName("평가 결과"),
        });

        StartEvent startEvent = new StartEvent();
        startEvent.setTracingTag("a1");
        processDefinition.addChildActivity(startEvent);

        businessRuleTask = new BusinessRuleTask();
        businessRuleTask.setTracingTag("a2");
        businessRuleTask.setBusinessRuleId("test-rule");
        businessRuleTask.setEventSynchronization(buildMappingContextEventSync());
        processDefinition.addChildActivity(businessRuleTask);

        DefaultActivity afterRule = new DefaultActivity();
        afterRule.setTracingTag("a3");
        processDefinition.addChildActivity(afterRule);

        processDefinition.addSequenceFlow(new SequenceFlow("a1", "a2"));
        processDefinition.addSequenceFlow(new SequenceFlow("a2", "a3"));

        processDefinition.afterDeserialization();

        originalFactory = getComponentFactory();
    }

    public void tearDown() throws Exception {
        GlobalContext.setComponentFactory(originalFactory != null ? originalFactory : new DefaultComponentFactory());
        AbstractProcessInstance.USE_CLASS = originalInstanceClass;
    }

    private void assertExecutionPathEquals(String message, String[] expected, ProcessInstance instance) {
        assertEquals(message, Arrays.asList(expected), instance.getActivityCompletionHistory());
    }

    public void testRuntimeEvaluatesAndCompletesInSingleStep() throws Exception {
        Map<String, Object> capturedInputs = new HashMap<>();
        GlobalContext.setComponentFactory(runtimeFactory((ruleId, inputs) -> {
            assertEquals("test-rule", ruleId);
            capturedInputs.putAll(inputs);
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("outcome", "approve");
            return outputs;
        }));

        ProcessInstance instance = processDefinition.createInstance();
        instance.set("신용도", 700);
        instance.execute();

        assertExecutionPathEquals("StartEvent → BusinessRuleTask → 다음 액티비티가 한 스텝에 모두 완료되어야 함",
                new String[] { "a1", "a2", "a3" }, instance);
        assertEquals("buildRuleInputs가 process variable에서 값을 읽어 DMN 입력 key로 전달해야 함",
                700, ((Number) capturedInputs.get("신용도")).intValue());
        assertEquals("DMN outcome이 mappingElement 규칙에 따라 '평가 결과' 변수로 반영되어야 함",
                "approve", instance.getBeanProperty("평가 결과"));
    }

    public void testFallbackWhenRuntimeNotRegistered() throws Exception {
        // DefaultComponentFactory는 BusinessRuleRuntime 인터페이스 조회 시 예외를 던진다 — 이 경우
        // ReceiveActivity 기본 흐름(메시지 리스너 등록)으로 폴백해 a2가 대기 상태로 머물러야 한다.
        GlobalContext.setComponentFactory(new DefaultComponentFactory());

        ProcessInstance instance = processDefinition.createInstance();
        instance.execute();

        assertExecutionPathEquals("Runtime 미등록 시 BusinessRuleTask는 ReceiveActivity 흐름으로 대기해야 함",
                new String[] { "a1" }, instance);
    }

    public void testSimulationCannotFallbackWhenRuntimeIsMissing() throws Exception {
        GlobalContext.setComponentFactory(new DefaultComponentFactory());
        ProcessInstance instance = processDefinition.createInstance();
        instance.getProcessTransactionContext().setSharedContext(BusinessRuleRuntime.SIMULATION_CONTEXT_KEY, Boolean.TRUE);
        try {
            instance.execute();
            fail("Simulation must fail instead of waiting for a runtime");
        } catch (Exception expected) {
            assertTrue(expected.toString().contains("runtime") || expected.getCause() != null);
        }
    }

    private EventSynchronization buildMappingContextEventSync() {
        EventSynchronization sync = new EventSynchronization();
        MappingContext mappingContext = new MappingContext();
        mappingContext.setMappingElements(new MappingElement[] {
                inputMapping("[businessRule].r_test-rule.in_신용도", "신용도"),
                outputMapping("평가 결과", "[businessRule].r_test-rule.out_outcome"),
        });
        sync.setMappingContext(mappingContext);
        return sync;
    }

    private MappingElement inputMapping(String argumentText, String processVariableName) {
        MappingElement m = new MappingElement();
        m.setArgument(textContext(argumentText));
        m.setVariable(ProcessVariable.forName(processVariableName));
        return m;
    }

    private MappingElement outputMapping(String processVariableName, String variableName) {
        MappingElement m = new MappingElement();
        m.setArgument(textContext(processVariableName));
        m.setVariable(ProcessVariable.forName(variableName));
        return m;
    }

    private static TextContext textContext(String text) {
        TextContext tc = TextContext.createInstance();
        tc.setText(text);
        return tc;
    }

    private static IComponentFactory runtimeFactory(BusinessRuleRuntime runtime) {
        return new DefaultComponentFactory() {
            @Override
            public <T> T getComponent(Class<T> clazz, Object[] constructorParameters) {
                if (clazz == BusinessRuleRuntime.class) {
                    return clazz.cast(runtime);
                }
                return super.getComponent(clazz, constructorParameters);
            }
        };
    }

    private static IComponentFactory getComponentFactory() throws Exception {
        java.lang.reflect.Field f = GlobalContext.class.getDeclaredField("componentFactory");
        f.setAccessible(true);
        return (IComponentFactory) f.get(null);
    }
}
