package org.uengine.five.businessrule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.*;
import org.uengine.kernel.*;
import org.uengine.kernel.bpmn.*;

class SimulationBusinessRuleContextTest {
    static final String ID = "simulation-dmn";
    private IComponentFactory original;
    private BusinessRuleEvaluator evaluator;
    private BusinessRuleRuntimeImpl runtime;
    private BusinessRuleStore store;
    private org.uengine.five.framework.ProcessTransactionContext transaction;

    public static String xml() {
        return """
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="definitions_test" name="Test" namespace="urn:test">
              <decision id="decision_test" name="Test"><decisionTable id="table_test" hitPolicy="FIRST">
                <input id="input_1"><inputExpression id="input_expression_1" typeRef="number"><text>amount</text></inputExpression></input>
                <output id="output_1" name="decision" typeRef="string"/>
                <annotation id="annotation_1" name="annotation"/>
                <rule id="rule_1"><inputEntry id="in_1"><text>&gt;= 100</text></inputEntry><outputEntry id="out_1"><text>"APPROVE"</text></outputEntry><annotationEntry id="note_1"><text>기존 설명</text></annotationEntry></rule>
                <rule id="rule_2"><inputEntry id="in_2"><text>-</text></inputEntry><outputEntry id="out_2"><text>"REJECT"</text></outputEntry><annotationEntry id="note_2"><text>거절 설명</text></annotationEntry></rule>
              </decisionTable></decision>
            </definitions>
            """;
    }

    static JsonNode json(String xml) { return new ObjectMapper().createObjectNode().put("dmnXml", xml); }

    @BeforeEach
    void setup() throws Exception {
        var field = GlobalContext.class.getDeclaredField("componentFactory");
        field.setAccessible(true);
        original = (IComponentFactory) field.get(null);
        evaluator = new BusinessRuleEvaluator();
        runtime = new BusinessRuleRuntimeImpl();
        runtime.businessRuleEvaluator = evaluator;
        store = mock(BusinessRuleStore.class);
        runtime.businessRuleStore = store;
        GlobalContext.setComponentFactory(new DefaultComponentFactory() {
            @Override public <T> T getComponent(Class<T> type, Object[] args) {
                return type == BusinessRuleRuntime.class ? type.cast(runtime) : super.getComponent(type, args);
            }
        });
        var constructor = org.uengine.five.framework.ProcessTransactionContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        transaction = constructor.newInstance();
    }

    @AfterEach
    void clear() throws Exception {
        if (org.uengine.five.framework.ProcessTransactionContext.currentOrNull() != null)
            org.uengine.five.framework.ProcessTransactionContext.currentOrNull().remove();
        GlobalContext.setComponentFactory(original);
    }

    @Test
    void actualCamundaEvaluatesImplicitAndExplicitFeelAndPreservesAnnotation() {
        for (String table : List.of(xml(), xml().replace("<inputExpression ", "<inputExpression expressionLanguage=\"feel\" ")) ) {
            SimulationBusinessRuleContext context = SimulationBusinessRuleContext.empty().withRule(ID, json(table), evaluator);
            assertEquals("APPROVE", context.evaluate(ID, Map.of("amount", 120.5), evaluator).get("decision"));
            assertEquals("REJECT", context.evaluate(ID, Map.of("amount", 10), evaluator).get("decision"));
            assertEquals("기존 설명", context.evaluate(ID, Map.of("amount", 120.5), evaluator).get("annotation"));
        }
    }

    @Test
    void pinUsesTheValidatedCopyAndNeverReloadsTheStore() throws Exception {
        ObjectNode raw = (ObjectNode) json(xml());
        SimulationBusinessRuleContext context = SimulationBusinessRuleContext.empty().withRule(ID, raw, evaluator);
        raw.put("dmnXml", "<malicious/>");
        context.pin(transaction);
        assertEquals("APPROVE", runtime.evaluate(ID, Map.of("amount", 120.5)).get("decision"));
        assertThrows(ResponseStatusException.class, () -> runtime.evaluate("unknown", Map.of()));
        verifyNoInteractions(store);
        transaction.rollback();
        when(store.loadOrThrow(ID)).thenReturn(new BusinessRuleStore.BusinessRuleFile(ID, "ordinary", "", json(xml())));
        assertEquals("REJECT", runtime.evaluate(ID, Map.of("amount", 10)).get("decision"));
        verify(store).loadOrThrow(ID);
    }

    @Test
    void emptyMarkerCannotFallbackAndUnsupportedInputsAreNotExposedToFeel() {
        SimulationBusinessRuleContext.empty().pin(transaction);
        assertThrows(ResponseStatusException.class, () -> runtime.evaluate(ID, Map.of()));
        SimulationBusinessRuleContext context = SimulationBusinessRuleContext.empty().withRule(ID, json(xml()), evaluator);
        assertEquals("APPROVE", context.evaluate(ID, Map.of("amount", 120.5, "unusedKernel", new ProcessDefinition()), evaluator).get("decision"));
        assertThrows(ResponseStatusException.class, () -> context.evaluate(ID, Map.of("amount", new ProcessDefinition()), evaluator));
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        assertThrows(ResponseStatusException.class, () -> context.evaluate(ID, Map.of("amount", cyclic), evaluator));
    }

    @ParameterizedTest
    @ValueSource(strings = { "juel", "javascript", "groovy", "unknown" })
    void rejectsEveryExplicitNonFeelExecutionLanguage(String language) {
        for (String node : List.of("inputExpression", "inputEntry", "outputEntry")) {
            String table = xml().replace("<" + node + " ", "<" + node + " expressionLanguage=\"" + language + "\" ");
            assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, json(table), evaluator));
        }
    }

    @Test
    void rejectsXmlEntitiesScriptsImportsAdvancedExpressionsAndLegacyRuleShadowing() {
        for (String table : List.of("<!DOCTYPE definitions SYSTEM \"file:///nonexistent-never-read\">" + xml(),
                xml().replace("<decision ", "<import namespace=\"https://example.invalid/never-call\"/><decision "),
                xml().replace("amount</text>", "get value(amount, &quot;class&quot;)</text>"),
                xml().replace("&gt;= 100", "contains(?, &quot;x&quot;)"),
                xml().replace("\"APPROVE\"", "java.lang.Runtime.getRuntime()"),
                xml().replace("hitPolicy=\"FIRST\"", "hitPolicy=\"COLLECT\""),
                xml().replace("<decision ", "<script/><decision "),
                xml().replace("<decision ", "<literalExpression/><decision "),
                "<definitions/>", "not xml")) {
            assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, json(table), evaluator), table);
        }
        ObjectNode mixed = (ObjectNode) json(xml());
        mixed.putArray("rules");
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, mixed, evaluator));
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID,
                new ObjectMapper().createObjectNode().putArray("rules"), evaluator));
    }

    @Test
    void rejectsFeelControlExpressionsBeforePreparationInsteadOfTreatingThemAsSpacedNames() {
        for (String expression : List.of("if true then 1 else 2", "for x in amount return x", "some x in amount satisfies x",
                "every x in amount satisfies x", "amount and amount", "amount or amount", "not amount",
                "amount between 1 and 2", "amount instance of number", "function x", "date and time")) {
            String output = xml().replace("\"APPROVE\"", expression);
            assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, json(output), evaluator), expression);
            String input = xml().replace("amount</text>", expression + "</text>");
            assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, json(input), evaluator), expression);
        }
        String undeclaredFunctionName = xml().replace("\"APPROVE\"", "now");
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, json(undeclaredFunctionName), evaluator));
    }

    @Test
    void realCamundaPreservesKoreanInputsAndSpacedProcessVariableMappings() throws Exception {
        for (String name : List.of("신용도", "credit_score", "평가금액")) {
            String table = xml().replace("amount</text>", name + "</text>")
                    .replace("typeRef=\"string\"", "typeRef=\"number\"")
                    .replace("\"APPROVE\"", name).replace("\"REJECT\"", "0");
            assertDoesNotThrow(() -> evaluator.evaluate(json(table), Map.of(name, 120.5)), name);
            SimulationBusinessRuleContext context = SimulationBusinessRuleContext.empty().withRule(ID, json(table), evaluator);
            Object decision = context.evaluate(ID, Map.of(name, 120.5), evaluator).get("decision");
            assertEquals(120.5, ((Number) decision).doubleValue());
        }
        String spacedDmn = xml().replace("amount</text>", "credit score</text>").replace("\"APPROVE\"", "credit score");
        assertThrows(RuntimeException.class, () -> evaluator.evaluate(json(spacedDmn), Map.of("credit score", 120.5)),
                "The existing engine rejects this spaced DMN expression; do not claim it succeeds");
        for (String source : List.of("입력 점수", "credit score")) {
            ProcessDefinition definition = new ProcessDefinition();
            definition.setProcessVariables(new ProcessVariable[] { ProcessVariable.forName(source), ProcessVariable.forName("평가 결과") });
            StartEvent start = new StartEvent(); start.setTracingTag("start");
            BusinessRuleTask task = new BusinessRuleTask(); task.setTracingTag("dmn"); task.setBusinessRuleId(ID);
            task.setEventSynchronization(sync(mapping("[businessRule].r_" + ID + ".in_amount", source),
                    mapping("평가 결과", "[businessRule].r_" + ID + ".out_decision")));
            DefaultActivity next = new DefaultActivity(); next.setTracingTag("next");
            for (Activity activity : List.of(start, task, next)) definition.addChildActivity(activity);
            SequenceFlow first = new SequenceFlow("start", "dmn"); first.setTracingTag("first");
            SequenceFlow second = new SequenceFlow("dmn", "next"); second.setTracingTag("second");
            definition.addSequenceFlow(first); definition.addSequenceFlow(second); definition.afterDeserialization();
            DefaultProcessInstance instance = new DefaultProcessInstance(definition, null, new HashMap<>());
            instance.setProcessTransactionContext(transaction); instance.set("", source, 120.5);
            SimulationBusinessRuleContext.empty().withRule(ID, json(xml()), evaluator).pin(transaction);
            instance.execute();
            assertEquals("APPROVE", instance.getBeanProperty("평가 결과"));
            assertEquals(Activity.STATUS_COMPLETED, instance.getStatus("next"));
        }
    }

    @Test
    void supportsLiteralDataAndReportsEngineUnsupportedEscapesAndQuantitativeLimits() {
        String table = xml().replace("\"APPROVE\"", "\"class (data)\"");
        assertEquals("class (data)", SimulationBusinessRuleContext.empty().withRule(ID, json(table), evaluator)
                .evaluate(ID, Map.of("amount", 120.5), evaluator).get("decision"));
        String escaped = xml().replace("\"APPROVE\"", "\"class \\" + "\"(data)\\" + "\"\"");
        assertThrows(RuntimeException.class, () -> evaluator.evaluate(json(escaped), Map.of("amount", 120.5)),
                "The existing Camunda 7.14 engine itself does not parse this quote escape");
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID, json(escaped), evaluator)
                .evaluate(ID, Map.of("amount", 120.5), evaluator));
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID,
                json(" ".repeat(1024 * 1024 + 1)), evaluator));
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID,
                json(xml().replace("amount</text>", "x".repeat(1025) + "</text>")), evaluator));
        String row = "<rule id=\"extra\"><inputEntry id=\"xe\"><text>-</text></inputEntry><outputEntry id=\"xo\"><text>0</text></outputEntry></rule>";
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID,
                json(xml().replace("</decisionTable>", row.repeat(1000) + "</decisionTable>")), evaluator));
        String input = "<input id=\"extra\"><inputExpression id=\"extra_expression\"><text>amount</text></inputExpression></input>";
        assertThrows(ResponseStatusException.class, () -> SimulationBusinessRuleContext.empty().withRule(ID,
                json(xml().replace("</decisionTable>", input.repeat(64) + "</decisionTable>")), evaluator));
    }

    @Test
    void projectsFormContextAsPlainFieldsAndPreservesZeroFalseAndNull() {
        String table = xml().replace("amount</text>", "intro.amount</text>");
        HtmlFormContext form = new HtmlFormContext();
        form.setValueMap(new HashMap<>(Map.of("amount", 120.5)));
        assertEquals("APPROVE", SimulationBusinessRuleContext.empty().withRule(ID, json(table), evaluator)
                .evaluate(ID, Map.of("intro", form), evaluator).get("decision"));
        assertEquals("REJECT", SimulationBusinessRuleContext.empty().withRule(ID, json(xml()), evaluator)
                .evaluate(ID, Map.of("amount", 0), evaluator).get("decision"));
        String bool = xml().replace("typeRef=\"number\"", "typeRef=\"boolean\"").replace("&gt;= 100", "false");
        assertEquals("APPROVE", SimulationBusinessRuleContext.empty().withRule(ID, json(bool), evaluator)
                .evaluate(ID, Map.of("amount", false), evaluator).get("decision"));
        assertNotNull(SimulationBusinessRuleContext.empty().withRule(ID, json(xml()), evaluator)
                .evaluate(ID, new HashMap<>(), evaluator));
    }

    @Test
    void realFormAndUrlCompletionRunDmnAndTheExpectedGatewayForBothBranches() throws Exception {
        for (boolean form : List.of(true, false)) for (double amount : List.of(120.5, 10.0)) {
            ProcessDefinition definition = new ProcessDefinition();
            definition.setId("dmn-test");
            definition.setProcessVariables(new ProcessVariable[] {ProcessVariable.forName("intro"), ProcessVariable.forName("amount"), ProcessVariable.forName("평가 결과")});
            StartEvent start = new StartEvent(); start.setTracingTag("start");
            HumanActivity user = form ? new FormActivity() : new URLActivity(); user.setTracingTag("input");
            if (form) ((FormActivity) user).setVariableForHtmlFormContext(ProcessVariable.forName("intro"));
            else user.setEventSynchronization(sync(mapping("amount", "[Arguments].amount")));
            BusinessRuleTask task = new BusinessRuleTask(); task.setTracingTag("dmn"); task.setBusinessRuleId(ID);
            task.setEventSynchronization(sync(mapping("[businessRule].r_" + ID + ".in_amount", form ? "intro.amount" : "amount"),
                    mapping("평가 결과", "[businessRule].r_" + ID + ".out_decision")));
            ExclusiveGateway gateway = new ExclusiveGateway(); gateway.setTracingTag("gateway");
            HumanActivity yes = new HumanActivity(); yes.setTracingTag("approve");
            HumanActivity no = new HumanActivity(); no.setTracingTag("reject");
            Role role = new Role(); role.setName("tester"); definition.setRoles(new Role[] {role});
            for (HumanActivity human : List.of(user, yes, no)) human.setRole(role);
            for (Activity activity : List.of(start, user, task, gateway, yes, no)) definition.addChildActivity(activity);
            definition.addSequenceFlow(new SequenceFlow("start", "input"));
            definition.addSequenceFlow(new SequenceFlow("input", "dmn"));
            definition.addSequenceFlow(new SequenceFlow("dmn", "gateway"));
            SequenceFlow approve = new SequenceFlow("gateway", "approve"); approve.setCondition(new Evaluate("평가 결과", "==", "APPROVE"));
            SequenceFlow reject = new SequenceFlow("gateway", "reject"); reject.setCondition(new Otherwise());
            definition.addSequenceFlow(approve); definition.addSequenceFlow(reject);
            for (int f = 0; f < definition.getSequenceFlows().size(); f++) definition.getSequenceFlows().get(f).setTracingTag("flow" + f);
            definition.afterDeserialization();
            DefaultProcessInstance instance = new DefaultProcessInstance(definition, null, new HashMap<>());
            instance.setProcessTransactionContext(transaction); instance.setSimulation(true);
            RoleMapping mapping = RoleMapping.create(); mapping.setName("tester"); mapping.setEndpoint("test"); mapping.setResourceName("Test");
            instance.putRoleMapping("tester", mapping);
            HtmlFormContext values = new HtmlFormContext(); values.setValueMap(new HashMap<>(Map.of("amount", amount))); instance.set("", "intro", values);
            SimulationBusinessRuleContext.empty().withRule(ID, json(xml()), evaluator).pin(transaction);
            instance.execute(); user.fireReceived(instance, form ? Map.of() : Map.of("amount", amount));
            assertEquals(amount >= 100 ? "APPROVE" : "REJECT", instance.getBeanProperty("평가 결과"));
            assertEquals(Activity.STATUS_RUNNING, instance.getStatus(amount >= 100 ? "approve" : "reject"));
            assertNotEquals(Activity.STATUS_RUNNING, instance.getStatus(amount >= 100 ? "reject" : "approve"));
        }
        verifyNoInteractions(store);
    }

    static MappingElement mapping(String argument, String variable) {
        MappingElement mapping = new MappingElement();
        TextContext text = TextContext.createInstance(); text.setText(argument); mapping.setArgument(text);
        mapping.setVariable(ProcessVariable.forName(variable)); return mapping;
    }
    static EventSynchronization sync(MappingElement... mappings) {
        MappingContext context = new MappingContext(); context.setMappingElements(mappings);
        EventSynchronization sync = new EventSynchronization(); sync.setMappingContext(context); return sync;
    }
}
