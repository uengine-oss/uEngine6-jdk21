package org.uengine.five.framework;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.uengine.five.businessrule.*;
import org.uengine.kernel.*;
import org.uengine.kernel.bpmn.*;
import org.uengine.processmanager.TransactionContext;

class SimulationBusinessRuleAdviceTest {
    private static final String XML = """
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs" name="Test" namespace="urn:test">
          <decision id="decision_test" name="Test"><decisionTable id="table_test" hitPolicy="FIRST">
            <input id="i"><inputExpression id="ie" typeRef="number"><text>amount</text></inputExpression></input>
            <output id="o" name="decision" typeRef="string"/>
            <rule id="r"><inputEntry id="re"><text>-</text></inputEntry><outputEntry id="ro"><text>"APPROVE"</text></outputEntry></rule>
          </decisionTable></decision>
        </definitions>
        """;

    @AfterEach void cleanup() throws Exception {
        if (ProcessTransactionContext.currentOrNull() != null) ProcessTransactionContext.currentOrNull().remove();
    }

    @Test void actualAdviceCleansSuccessRollbackAndBeforeCommitFailureBeforeTheNextOrdinaryRequest() throws Exception {
        BusinessRuleEvaluator evaluator = new BusinessRuleEvaluator();
        var json = new ObjectMapper().createObjectNode().put("dmnXml", XML);
        BusinessRuleStore store = mock(BusinessRuleStore.class);
        when(store.loadOrThrow("rule")).thenReturn(new BusinessRuleStore.BusinessRuleFile("rule", "ordinary", "", json));
        BusinessRuleRuntimeImpl runtime = new BusinessRuleRuntimeImpl();
        ReflectionTestUtils.setField(runtime, "businessRuleStore", store);
        ReflectionTestUtils.setField(runtime, "businessRuleEvaluator", evaluator);
        Service target = new Service(); target.runtime = runtime;
        target.snapshot = SimulationBusinessRuleContext.empty().withRule("rule", json, evaluator);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new ProcessTransactionAdvice());
        Service proxy = factory.getProxy();
        for (String mode : List.of("success", "rollback", "beforeCommit")) {
            if (mode.equals("success")) proxy.simulate(mode);
            else assertThrows(Exception.class, () -> proxy.simulate(mode));
            assertNull(ProcessTransactionContext.currentOrNull(), mode + " must remove the transaction");
            assertEquals("APPROVE", proxy.ordinary().get("decision"));
            assertNull(ProcessTransactionContext.currentOrNull());
        }
        verify(store, times(3)).loadOrThrow("rule");
        assertEquals(1, target.afterCommits);
    }

    public static class Service {
        BusinessRuleRuntimeImpl runtime;
        SimulationBusinessRuleContext snapshot;
        int afterCommits;
        @ProcessTransactional public void simulate(String mode) throws Exception {
            ProcessTransactionContext transaction = ProcessTransactionContext.getThreadLocalInstance();
            snapshot.pin(transaction);
            transaction.addTransactionListener(new TransactionListener() {
                public void beforeCommit(TransactionContext tx) throws Exception {
                    assertSame(snapshot, SimulationBusinessRuleContext.current(transaction));
                    runtime.evaluate("rule", Map.of("amount", 120.5));
                    if (mode.equals("beforeCommit")) throw new IllegalArgumentException("actual beforeCommit failure");
                }
                public void afterCommit(TransactionContext tx) { afterCommits++; }
                public void beforeRollback(TransactionContext tx) { }
                public void afterRollback(TransactionContext tx) { }
            });
            if (mode.equals("rollback")) throw new IllegalArgumentException("actual target failure");
        }
        @ProcessTransactional public Map<String, Object> ordinary() throws Exception {
            assertNull(SimulationBusinessRuleContext.current(ProcessTransactionContext.currentOrNull()));
            return runtime.evaluate("rule", Map.of("amount", 10));
        }
    }

    @Test void embeddedSubprocessBeforeCommitExecutesParentDmnFromPinnedSnapshot() throws Exception {
        BusinessRuleEvaluator evaluator = new BusinessRuleEvaluator();
        var json = new ObjectMapper().createObjectNode().put("dmnXml", XML);
        BusinessRuleRuntimeImpl runtime = new BusinessRuleRuntimeImpl();
        BusinessRuleStore store = mock(BusinessRuleStore.class);
        ReflectionTestUtils.setField(runtime, "businessRuleStore", store);
        ReflectionTestUtils.setField(runtime, "businessRuleEvaluator", evaluator);
        var field = GlobalContext.class.getDeclaredField("componentFactory"); field.setAccessible(true);
        IComponentFactory original = (IComponentFactory) field.get(null);
        GlobalContext.setComponentFactory(new DefaultComponentFactory() {
            @Override public <T> T getComponent(Class<T> type, Object[] args) {
                return type == BusinessRuleRuntime.class ? type.cast(runtime) : super.getComponent(type, args);
            }
        });
        try {
            ProcessTransactionContext transaction = new ProcessTransactionContext();
            SimulationBusinessRuleContext.empty().withRule("rule", json, evaluator).pin(transaction);
            ProcessDefinition definition = new ProcessDefinition();
            definition.setProcessVariables(new ProcessVariable[] { ProcessVariable.forName("amount") });
            StartEvent start = new StartEvent(); start.setTracingTag("start");
            SubProcess sub = new SubProcess(); sub.setTracingTag("sub");
            StartEvent innerStart = new StartEvent(); innerStart.setTracingTag("innerStart");
            EndEvent innerEnd = new EndEvent(); innerEnd.setTracingTag("innerEnd");
            sub.addChildActivity(innerStart); sub.addChildActivity(innerEnd); sub.addSequenceFlow(new SequenceFlow("innerStart", "innerEnd"));
            BusinessRuleTask task = new BusinessRuleTask(); task.setTracingTag("dmn"); task.setBusinessRuleId("rule");
            DefaultActivity after = new DefaultActivity(); after.setTracingTag("after");
            for (Activity activity : List.of(start, sub, task, after)) definition.addChildActivity(activity);
            definition.addSequenceFlow(new SequenceFlow("start", "sub")); definition.addSequenceFlow(new SequenceFlow("sub", "dmn"));
            definition.addSequenceFlow(new SequenceFlow("dmn", "after")); definition.afterDeserialization();
            DefaultProcessInstance instance = new DefaultProcessInstance(definition, null, new HashMap<>());
            instance.setProcessTransactionContext(transaction); instance.set("", "amount", 120.5); instance.execute();
            assertNotEquals(Activity.STATUS_COMPLETED, instance.getStatus("dmn"));
            transaction.commit();
            assertEquals(Activity.STATUS_COMPLETED, instance.getStatus("dmn"));
            assertEquals(Activity.STATUS_COMPLETED, instance.getStatus("after"));
            assertNull(ProcessTransactionContext.currentOrNull());
            verifyNoInteractions(store);
        } finally { GlobalContext.setComponentFactory(original); }
    }
}
