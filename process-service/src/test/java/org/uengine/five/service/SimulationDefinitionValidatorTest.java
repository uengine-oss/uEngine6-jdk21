package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.bsf.BSFManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.EventSynchronization;
import org.uengine.contexts.HtmlFormContext;
import org.uengine.contexts.MappingContext;
import org.uengine.contexts.TextContext;
import org.uengine.five.dto.ProcessExecutionCommand;
import org.uengine.five.dto.StartAndCompleteCommand;
import org.uengine.five.dto.WorkItemResource;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.messaging.TypedJsonObjectMapperFactory;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.serializers.BpmnXMLParser;
import org.uengine.kernel.*;
import org.uengine.kernel.bpmn.*;
import org.uengine.processdesigner.mapper.TransformerMapping;
import org.uengine.processdesigner.mapper.transformers.BeanValueTransformer;
import org.uengine.processdesigner.mapper.transformers.DirectValueTransformer;
import org.uengine.processdesigner.mapper.transformers.JsonBuildTransformer;
import org.uengine.processdesigner.mapper.transformers.JsonParseTransformer;

class SimulationDefinitionValidatorTest {
    @Test
    void actualGuardedGetIsPureAndExecutionPreparePinsAtomicallyWithoutReloading() throws Exception {
        var field = GlobalContext.class.getDeclaredField("componentFactory"); field.setAccessible(true);
        IComponentFactory original = (IComponentFactory) field.get(null);
        org.uengine.five.framework.ProcessTransactionContext transaction = null;
        try {
            var evaluator = new org.uengine.five.businessrule.BusinessRuleEvaluator();
            var runtime = new org.uengine.five.businessrule.BusinessRuleRuntimeImpl();
            var store = mock(org.uengine.five.businessrule.BusinessRuleStore.class);
            org.springframework.test.util.ReflectionTestUtils.setField(runtime, "businessRuleStore", store);
            org.springframework.test.util.ReflectionTestUtils.setField(runtime, "businessRuleEvaluator", evaluator);
            GlobalContext.setComponentFactory(new DefaultComponentFactory() {
                @Override public <T> T getComponent(Class<T> type, Object[] args) {
                    return type == BusinessRuleRuntime.class ? type.cast(runtime) : super.getComponent(type, args);
                }
            });
            String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs" name="Test" namespace="urn:test">
                  <decision id="decision_test" name="Test"><decisionTable id="table_test" hitPolicy="FIRST">
                    <input id="i"><inputExpression id="ie" typeRef="number"><text>amount</text></inputExpression></input>
                    <output id="o" name="decision" typeRef="string"/>
                    <rule id="r"><inputEntry id="re"><text>-</text></inputEntry><outputEntry id="ro"><text>"APPROVE"</text></outputEntry></rule>
                  </decisionTable></decision>
                </definitions>
                """;
            var json = new ObjectMapper().createObjectNode().put("dmnXml", xml);
            when(store.loadOrThrow("rule")).thenReturn(new org.uengine.five.businessrule.BusinessRuleStore.BusinessRuleFile("rule", "Test", "", json));
            HumanActivity human = new HumanActivity(); human.setTracingTag("human");
            BusinessRuleTask task = new BusinessRuleTask(); task.setBusinessRuleId("rule");
            ProcessDefinition definition = definition(human, task);
            InstanceServiceImpl service = service(definition); service.businessRuleStore = store; service.businessRuleEvaluator = evaluator;
            org.springframework.test.util.ReflectionTestUtils.setField(service, "simulationBusinessRuleRuntime", runtime);
            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getProcessDefinition()).thenReturn(definition);
            when(instance.getProperty("human", Activity.PVKEY_STATUS)).thenReturn(Activity.STATUS_RUNNING);
            doReturn(instance).when(service).getProcessInstanceLocal("101");
            when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist()));
            MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("isSimulate", "true");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            assertNull(org.uengine.five.framework.ProcessTransactionContext.currentOrNull());
            service.getWorkItem("1");
            assertNull(org.uengine.five.framework.ProcessTransactionContext.currentOrNull(), "GET must not create/pin a transaction");
            assertThrows(ResponseStatusException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "validateSimulation", definition, true),
                    "DMN execution requires an existing transaction");
            assertNull(org.uengine.five.framework.ProcessTransactionContext.currentOrNull());
            var constructor = org.uengine.five.framework.ProcessTransactionContext.class.getDeclaredConstructor(); constructor.setAccessible(true);
            transaction = constructor.newInstance();
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "validateSimulation", definition, true);
            var snapshot = org.uengine.five.businessrule.SimulationBusinessRuleContext.current(transaction);
            assertNotNull(snapshot);
            json.put("dmnXml", "<changed/>");
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "validateSimulation", definition, true);
            assertSame(snapshot, org.uengine.five.businessrule.SimulationBusinessRuleContext.current(transaction));
            assertEquals("APPROVE", runtime.evaluate("rule", Map.of("amount", 10)).get("decision"));
            verify(store, times(3)).loadOrThrow("rule"); // pure GET, missing-TC rejection, first execution prepare
            task.setEventSynchronization(sync(mapping("bad", "[instance].processDefinition")));
            assertThrows(ResponseStatusException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "validateSimulation", definition, true));
            assertSame(snapshot, org.uengine.five.businessrule.SimulationBusinessRuleContext.current(transaction));
        } finally {
            if (transaction != null) transaction.remove();
            GlobalContext.setComponentFactory(original);
        }
    }

    @Test
    void supportsOnlyExactDmnSyntheticMappingsAndSafeDeclaredVariableNames() {
        BusinessRuleTask task = new BusinessRuleTask(); task.setBusinessRuleId("룰-1");
        MappingElement input = mapping("신용도", "[businessRule].r_룰-1.in_amount");
        MappingElement output = mapping("[businessRule].r_룰-1.out_decision", "평가 결과");
        MappingContext context = new MappingContext(); context.setMappingElements(new MappingElement[] {input, output});
        EventSynchronization sync = new EventSynchronization(); sync.setMappingContext(context); task.setEventSynchronization(sync);
        ProcessDefinition definition = definition(task);
        definition.setProcessVariables(new ProcessVariable[] {ProcessVariable.forName("신용도"), ProcessVariable.forName("평가 결과")});
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition));
        assertDoesNotThrow(() -> SimulationDefinitionValidator.validate(definition, rule -> { }));
        for (String unsafe : List.of("../rule", "x/y", "x\\y", "x%2fy", "x.y", "x:y")) {
            task.setBusinessRuleId(unsafe);
            assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition, rule -> { }));
        }
        task.setBusinessRuleId("룰-1");
        input.getArgument().setText("[businessRule].r_other.in_amount");
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition, rule -> { }));
        input.getArgument().setText("[businessRule].r_룰-1.in_amount");
        input.setTransformerMapping(transformer(new DirectValueTransformer()));
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition, rule -> { }));
        input.setTransformerMapping(null);
        definition.setProcessVariables(new ProcessVariable[] {ProcessVariable.forName("[instance].class")});
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition, rule -> { }));
        definition.setProcessVariables(null);
        task.setQueuingEnabled(true);
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition, rule -> { }));
        task.setQueuingEnabled(false); task.setFaultTolerant(true);
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition, rule -> { }));
    }

    @Test
    void everySimulationEntryRejectsMissingDmnRuntimeBeforeAnyExecution() throws Exception {
        BusinessRuleTask task = new BusinessRuleTask(); task.setBusinessRuleId("missing-runtime");
        ProcessDefinition definition = definition(task);
        InstanceServiceImpl service = service(definition);
        ProcessExecutionCommand command = new ProcessExecutionCommand(); command.setProcessDefinitionId("unsafe"); command.setSimulation(true);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.start(command)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.dryRun(command)).getStatusCode().value());
        StartAndCompleteCommand combined = new StartAndCompleteCommand(); combined.setProcessExecutionCommand(command);
        assertThrows(ResponseStatusException.class, () -> service.startAndComplete(combined, "true"));
        ProcessInstance instance = mock(ProcessInstance.class); when(instance.getProcessDefinition()).thenReturn(definition);
        when(instance.getStatus("")).thenReturn(Activity.STATUS_RUNNING);
        doReturn(instance).when(service).getProcessInstanceLocal("101");
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist()));
        assertThrows(ResponseStatusException.class, () -> service.putWorkItemComplete("1", new WorkItemResource(), "true"));
        assertThrows(ResponseStatusException.class, () -> service.advanceToActivity("101", "target", Map.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("isSimulate", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThrows(ResponseStatusException.class, () -> service.getWorkItem("1"));
        verify(instance, never()).getBeanProperty(anyString()); verify(instance, never()).execute(anyString());
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    static Stream<Activity> unsupportedActivities() {
        return Stream.of(new ServiceTask(), new ScriptActivity(), new SendTask(), new CallActivity(),
                new LocalEMailActivity(), new ReceiveTask(), new HumanActivity() { });
    }

    @ParameterizedTest
    @MethodSource("unsupportedActivities")
    void rejectsUnsafeAndUnknownActivitiesBeforeExecution(Activity unsafe) {
        unsafe.setTracingTag("external");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> SimulationDefinitionValidator.validate(definition(unsafe)));
        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("external"));
    }

    @Test
    void checksNestedActivitiesBeforeStartingTheParent() {
        SubProcess child = new SubProcess();
        child.setTracingTag("embedded");
        ServiceTask unsafe = new ServiceTask();
        unsafe.setTracingTag("nested-api");
        child.addChildActivity(unsafe);
        assertTrue(assertThrows(ResponseStatusException.class,
                () -> SimulationDefinitionValidator.validate(definition(child))).getReason().contains("nested-api"));
    }

    @Test
    void rejectsScriptConditionsAndCustomHandlersAndFilters() {
        ProcessDefinition definition = definition(new HumanActivity());
        SequenceFlow flow = new SequenceFlow();
        flow.setCondition(new ExpressionEvaluateCondition("false"));
        definition.getSequenceFlows().add(flow);
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition));
        definition.getSequenceFlows().clear();
        EventHandler handler = new EventHandler();
        handler.setHandlerActivity(new DefaultActivity());
        definition.setEventHandlers(new EventHandler[] { handler });
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition));
        definition.setEventHandlers(null);
        definition.setActivityFilters(new ActivityFilter[] { mock(ActivityFilter.class) });
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition));
    }

    @Test
    void permitsSimpleConditionAndTemplateButRejectsExecutableExpressions() {
        URLActivity url = new URLActivity();
        url.setUrl("https://example.invalid/customer/{customer.id}");
        ProcessDefinition definition = definition(url);
        definition.setInstanceNamePattern("Case <%=instance.name%>");
        SequenceFlow flow = new SequenceFlow();
        flow.setCondition(new And(new Evaluate("amount", ">", 0), new Not(new Otherwise())));
        definition.getSequenceFlows().add(flow);
        assertDoesNotThrow(() -> SimulationDefinitionValidator.validate(definition));
        url.setUrl("{T(java.lang.Runtime).getRuntime()}");
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition));
    }

    @Test
    void validatesTransformerChainsAndRejectsReflectionPaths() {
        HumanActivity human = new HumanActivity();
        MappingElement mapping = mapping("[Arguments].input", "result");
        human.setEventSynchronization(sync(mapping));
        DirectValueTransformer direct = new DirectValueTransformer();
        direct.setValue(Map.of("ok", List.of(0, false, "x")));
        JsonBuildTransformer build = new JsonBuildTransformer();
        build.getArgumentSourceMap().put("val1", transformer(direct));
        JsonParseTransformer parse = new JsonParseTransformer();
        parse.getArgumentSourceMap().put("json", transformer(build));
        mapping.setTransformerMapping(transformer(parse));
        assertDoesNotThrow(() -> SimulationDefinitionValidator.validate(definition(human)));
        build.getArgumentSourceMap().put("val1", transformer(new BeanValueTransformer()));
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition(human)));
        mapping.setTransformerMapping(null);
        mapping.getArgument().setText("[instance].processInstanceEntity.custId");
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition(human)));
    }

    @Test
    void rejectsNonJsonDirectValuesAndCyclicTransformerChains() {
        HumanActivity human = new HumanActivity();
        MappingElement element = mapping("[Arguments].x", "result");
        human.setEventSynchronization(sync(element));
        DirectValueTransformer direct = new DirectValueTransformer();
        direct.setValue(new Object());
        element.setTransformerMapping(transformer(direct));
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition(human)));
        JsonBuildTransformer build = new JsonBuildTransformer();
        build.getArgumentSourceMap().put("val1", transformer(build));
        element.setTransformerMapping(transformer(build));
        assertThrows(ResponseStatusException.class, () -> SimulationDefinitionValidator.validate(definition(human)));
    }

    @Test
    void startDryRunAndStartAndCompleteRejectBeforeNetworkOrScriptConstruction() throws Exception {
        try (MockedConstruction<RestTemplate> network = mockConstruction(RestTemplate.class);
                MockedConstruction<BSFManager> scripts = mockConstruction(BSFManager.class)) {
            for (Activity activity : List.of(new ServiceTask(), new ScriptActivity())) {
                InstanceServiceImpl service = service(definition(activity));
                ProcessExecutionCommand command = new ProcessExecutionCommand();
                command.setProcessDefinitionId("unsafe");
                command.setSimulation(true);
                assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.start(command)).getStatusCode().value());
                assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.dryRun(command)).getStatusCode().value());
                StartAndCompleteCommand combined = new StartAndCompleteCommand();
                combined.setProcessExecutionCommand(command);
                combined.setWorkItem(new WorkItemResource());
                assertEquals(400, assertThrows(ResponseStatusException.class,
                        () -> service.startAndComplete(combined, "true")).getStatusCode().value());
            }
            assertTrue(network.constructed().isEmpty(), "No RestTemplate may be constructed");
            assertTrue(scripts.constructed().isEmpty(), "No script engine may be constructed");
        }
    }

    @Test
    void completionAndAdvanceRejectBeforeReadingInputsOrExecutingActivities() throws Exception {
        ProcessDefinition definition = definition(new ServiceTask());
        InstanceServiceImpl service = service(definition);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessDefinition()).thenReturn(definition);
        when(instance.getStatus("")).thenReturn(Activity.STATUS_RUNNING);
        doReturn(instance).when(service).getProcessInstanceLocal("101");
        WorklistEntity worklist = worklist();
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist));
        assertThrows(ResponseStatusException.class,
                () -> service.putWorkItemComplete("1", new WorkItemResource(), "true"));
        assertThrows(ResponseStatusException.class, () -> service.advanceToActivity("101", "target", Map.of()));
        verify(instance, never()).getBeanProperty(anyString());
        verify(instance, never()).execute(anyString());
    }

    @Test
    void simulationGetChecksTheActualInstanceDefinitionBeforeInputMapping() throws Exception {
        HumanActivity human = new HumanActivity();
        human.setTracingTag("human");
        ProcessDefinition unsafe = definition(human, new ServiceTask());
        InstanceServiceImpl service = service(unsafe);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessDefinition()).thenReturn(unsafe);
        doReturn(instance).when(service).getProcessInstanceLocal("101");
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("isSimulate", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThrows(ResponseStatusException.class, () -> service.getWorkItem("1"));
        verifyNoInteractions(service.definitionService);
        verify(instance, never()).getBeanProperty(anyString());
    }

    @Test
    void ordinaryWorkItemReadKeepsItsExistingVersionPath() throws Exception {
        HumanActivity human = new HumanActivity();
        human.setTracingTag("human");
        ProcessDefinition definition = definition(human, new ServiceTask());
        InstanceServiceImpl service = service(definition);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProperty("human", Activity.PVKEY_STATUS)).thenReturn(Activity.STATUS_RUNNING);
        doReturn(instance).when(service).getProcessInstanceLocal("101");
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist()));
        when(service.definitionService.getDefinition("unsafe", "v1")).thenReturn(definition);
        assertSame(human, service.getWorkItem("1").getActivity());
        verify(service.definitionService).getDefinition("unsafe", "v1");
    }

    static Stream<ObjectMapper> responseMappers() {
        return Stream.of(new ObjectMapper(), TypedJsonObjectMapperFactory.create());
    }

    @ParameterizedTest
    @MethodSource("responseMappers")
    void realDtoExposesOnlySafeSimulationFormAndParameterMetadata(ObjectMapper mapper) throws Exception {
        FormActivity form = formWithTypedParameter();
        definition(form); // A live parent graph must remain hidden.
        WorkItemResource resource = new WorkItemResource();
        resource.setActivity(form);
        resource.setIncludeSimulationMetadata(true);
        resource.setParameterValues(Map.of("amount", 0, "accepted", false));
        String response = mapper.writeValueAsString(resource);
        JsonNode json = mapper.readTree(response);
        assertEquals("intro", json.path("activity").path("variableForHtmlFormContext").path("name").asText());
        JsonNode parameters = json.path("activity").path("parameters");
        // Project typed mapper wraps Lists with their concrete type; ordinary HTTP mapper does not.
        if (parameters.size() == 2 && parameters.get(0).isTextual()) parameters = parameters.get(1);
        assertEquals("in", parameters.get(0).path("direction").asText());
        assertEquals("amount", parameters.get(0).path("argument").path("text").asText());
        assertEquals("java.lang.Number", parameters.get(0).path("variable").path("typeClassName").asText());
        assertEquals(0, json.path("parameterValues").path("amount").asInt());
        assertFalse(json.path("parameterValues").path("accepted").asBoolean());
        assertFalse(response.contains("includeSimulationMetadata"));
        assertFalse(response.contains("parentActivity"));
        assertFalse(response.contains("transformerMapping"));
        assertFalse(response.contains("private-default-value"));
        assertFalse(response.contains("org.uengine.kernel.FormActivity"));
    }

    @ParameterizedTest
    @MethodSource("responseMappers")
    void ordinaryDtoRetainsItsExistingSummaryWithoutSimulationMetadata(ObjectMapper mapper) throws Exception {
        WorkItemResource resource = new WorkItemResource();
        resource.setActivity(formWithTypedParameter());
        JsonNode json = mapper.readTree(mapper.writeValueAsString(resource));
        assertFalse(json.path("activity").has("variableForHtmlFormContext"));
        assertFalse(json.path("activity").has("parameters"));
        assertFalse(json.has("includeSimulationMetadata"));
    }

    @ParameterizedTest
    @MethodSource("responseMappers")
    void requestBodyCannotEnableTheServerOnlyMetadataFlag(ObjectMapper mapper) throws Exception {
        WorkItemResource resource = mapper.readValue("{\"includeSimulationMetadata\":true}", WorkItemResource.class);
        resource.setActivity(formWithTypedParameter());
        JsonNode json = mapper.readTree(mapper.writeValueAsString(resource));
        assertFalse(json.path("activity").has("variableForHtmlFormContext"));
        assertFalse(json.path("activity").has("parameters"));
        assertFalse(json.has("includeSimulationMetadata"));
    }

    @ParameterizedTest
    @MethodSource("responseMappers")
    void metadataHandlesMissingBindingsParametersAndTypeHintsWithoutResolvingClasses(ObjectMapper mapper) throws Exception {
        FormActivity form = new FormActivity();
        WorkItemResource resource = new WorkItemResource();
        resource.setActivity(form);
        resource.setIncludeSimulationMetadata(true);
        JsonNode empty = mapper.readTree(mapper.writeValueAsString(resource));
        assertFalse(empty.path("activity").has("variableForHtmlFormContext"));
        assertFalse(empty.path("activity").has("parameters"));
        ParameterContext parameter = new ParameterContext();
        parameter.setVariable(new ProcessVariable() {
            @Override
            public Class getType() { throw new AssertionError("Must not resolve dynamic binding classes"); }
        });
        form.setParameters(new ParameterContext[] { parameter });
        String response = assertDoesNotThrow(() -> mapper.writeValueAsString(resource));
        assertFalse(response.contains("includeSimulationMetadata"));
        assertFalse(response.contains("xmlBindingClassName"));
    }

    @Test
    void guardedWorkItemResponseIncludesTheActualFormBindingAndPreservesInputs() throws Exception {
        FormActivity form = formWithTypedParameter();
        form.setTracingTag("human");
        ProcessDefinition definition = definition(form);
        InstanceServiceImpl service = service(definition);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessDefinition()).thenReturn(definition);
        when(instance.getProperty("human", Activity.PVKEY_STATUS)).thenReturn(Activity.STATUS_RUNNING);
        when(instance.get("", "amount")).thenReturn(0);
        doReturn(instance).when(service).getProcessInstanceLocal("101");
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("isSimulate", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(service.getWorkItem("1")));
        assertEquals("intro", json.path("activity").path("variableForHtmlFormContext").path("name").asText());
        assertEquals(0, json.path("parameterValues").path("amount").asInt());
        verifyNoInteractions(service.definitionService);
    }

    @Test
    void realTaskVariableRequestMapperPreservesTheCanonicalFormIdentityAndValues() throws Exception {
        String json = """
            {"_type":"org.uengine.contexts.HtmlFormContext",
             "formDefId":"simulation-external-intro-20261109",
             "filePath":"simulation-external-intro-20261109.form",
             "valueMap":{"message":"기존폼"}}
            """;
        HtmlFormContext parsed = assertInstanceOf(HtmlFormContext.class,
                InstanceServiceImpl.arrayObjectMapper.readValue(json, Serializable.class));
        assertEquals("simulation-external-intro-20261109", parsed.getFormDefId());
        assertEquals("simulation-external-intro-20261109.form", parsed.getFilePath());
        assertEquals("기존폼", parsed.getValueMap().get("message"));

        FormActivity form = formWithTypedParameter();
        form.setTracingTag("human");
        ProcessDefinition definition = definition(form);
        InstanceServiceImpl service = service(definition);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getProcessDefinition()).thenReturn(definition);
        when(instance.getProperty("human", Activity.PVKEY_STATUS)).thenReturn(Activity.STATUS_RUNNING);
        when(instance.get("", "amount")).thenReturn(0);
        doReturn(instance).when(service).getProcessInstanceLocal("101");
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(worklist()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("isSimulate", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.setVariableWithTaskId("101", "1", "intro", json);

        ArgumentCaptor<Serializable> stored = ArgumentCaptor.forClass(Serializable.class);
        verify(instance).set(eq(""), eq("intro"), stored.capture());
        HtmlFormContext actual = assertInstanceOf(HtmlFormContext.class, stored.getValue());
        assertEquals(parsed.getFormDefId(), actual.getFormDefId());
        assertEquals(parsed.getFilePath(), actual.getFilePath());
        assertEquals(parsed.getValueMap(), actual.getValueMap());
        verify(service).getWorkItem("1"); // The real guarded GET path is reused before the real request mapper.
        verifyNoInteractions(service.definitionService);
    }

    @Test
    void realFormRequestMapperRejectsThePreviouslyIncompleteContext() {
        String json = """
            {"_type":"org.uengine.contexts.HtmlFormContext","valueMap":{"message":"기존폼"}}
            """;
        assertThrows(Exception.class,
                () -> InstanceServiceImpl.arrayObjectMapper.readValue(json, Serializable.class));
    }

    @Test
    void realEngineMapsManualAndUrlInputsThenCompletesTheExistingFormAndBranch() throws Exception {
        String xml = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:uengine="http://uengine">
              <bpmn:process id="simulation-external" isExecutable="true">
                <bpmn:extensionElements><uengine:properties>
                  <uengine:variable name="amount" type="Number"><uengine:json>{}</uengine:json></uengine:variable>
                  <uengine:variable name="approved" type="Text"><uengine:json>{}</uengine:json></uengine:variable>
                  <uengine:variable name="form" type="Form"><uengine:json>{}</uengine:json></uengine:variable>
                </uengine:properties></bpmn:extensionElements>
                <bpmn:startEvent id="start"/>
                <bpmn:userTask id="manual"/>
                <bpmn:userTask id="url"><bpmn:extensionElements><uengine:properties><uengine:json>
                  {"_type":"org.uengine.kernel.URLActivity","url":"https://example.invalid/never-open",
                   "eventSynchronization":{"mappingContext":{"mappingElements":[
                     {"argument":{"text":"amount"},"variable":{"name":"[Arguments].amount"}}
                   ]}}}
                </uengine:json></uengine:properties></bpmn:extensionElements></bpmn:userTask>
                <bpmn:userTask id="formTask"><bpmn:extensionElements><uengine:properties><uengine:json>
                  {"_type":"org.uengine.kernel.FormActivity","variableForHtmlFormContext":{"name":"form"},
                   "eventSynchronization":{"mappingContext":{"mappingElements":[
                     {"argument":{"text":"approved"},"variable":{"name":"form.approved"}}
                   ]}}}
                </uengine:json></uengine:properties></bpmn:extensionElements></bpmn:userTask>
                <bpmn:exclusiveGateway id="branch"/>
                <bpmn:userTask id="next"/>
                <bpmn:sequenceFlow id="a" sourceRef="start" targetRef="manual"/>
                <bpmn:sequenceFlow id="b" sourceRef="manual" targetRef="url"/>
                <bpmn:sequenceFlow id="c" sourceRef="url" targetRef="formTask"/>
                <bpmn:sequenceFlow id="d" sourceRef="formTask" targetRef="branch"/>
                <bpmn:sequenceFlow id="e" sourceRef="branch" targetRef="next"><bpmn:extensionElements><uengine:properties><uengine:json>
                  {"condition":{"_type":"org.uengine.kernel.Evaluate","key":"approved","value":"yes","condition":"=="}}
                </uengine:json></uengine:properties></bpmn:extensionElements></bpmn:sequenceFlow>
              </bpmn:process>
            </bpmn:definitions>
            """;
        ProcessDefinition definition = new BpmnXMLParser().parse(xml);
        definition.setId("simulation-external");
        Role role = new Role();
        role.setName("tester");
        definition.setRoles(new Role[] { role });
        for (String tag : List.of("manual", "url", "formTask", "next")) ((HumanActivity) definition.getActivity(tag)).setRole(role);
        SimulationDefinitionValidator.validate(definition);
        DefaultProcessInstance instance = new DefaultProcessInstance(definition, null, new HashMap<>());
        instance.setSimulation(true);
        RoleMapping assignee = RoleMapping.create();
        assignee.setName("tester");
        assignee.setEndpoint("simulation-test");
        assignee.setResourceName("Simulation tester");
        instance.putRoleMapping("tester", assignee);
        HtmlFormContext form = new HtmlFormContext();
        form.setValueMap(new HashMap<>(Map.of("approved", "yes")));
        instance.set("", "form", form);
        try (MockedConstruction<RestTemplate> network = mockConstruction(RestTemplate.class)) {
            instance.execute();
            ((HumanActivity) definition.getActivity("manual")).fireReceived(instance, Map.of());
            assertEquals(Activity.STATUS_RUNNING, instance.getStatus("url"));
            ((HumanActivity) definition.getActivity("url")).fireReceived(instance, Map.of("amount", 0));
            assertEquals(0, instance.get("", "amount"));
            assertEquals(Activity.STATUS_RUNNING, instance.getStatus("formTask"));
            ((HumanActivity) definition.getActivity("formTask")).fireReceived(instance, Map.of());
            assertEquals("yes", instance.get("", "approved"));
            assertEquals(Activity.STATUS_RUNNING, instance.getStatus("next"));
            assertTrue(network.constructed().isEmpty());
        }
    }

    private ProcessDefinition definition(Activity... activities) {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId("unsafe");
        definition.setTracingTag("");
        for (int i = 0; i < activities.length; i++) {
            if (activities[i].getTracingTag() == null) activities[i].setTracingTag("activity" + i);
            definition.addChildActivity(activities[i]);
        }
        return definition;
    }

    private FormActivity formWithTypedParameter() {
        FormActivity form = new FormActivity();
        ProcessVariable binding = new ProcessVariable();
        binding.setName("intro");
        binding.setDefaultValue(Map.of("secret", "private-default-value"));
        form.setVariableForHtmlFormContext(binding);
        ParameterContext parameter = new ParameterContext();
        parameter.setDirection("in");
        TextContext argument = TextContext.createInstance();
        argument.setText("amount");
        parameter.setArgument(argument);
        ProcessVariable variable = new ProcessVariable();
        variable.setName("amount");
        variable.setTypeClassName("java.lang.Number");
        parameter.setVariable(variable);
        parameter.setTransformerMapping(transformer(new DirectValueTransformer()));
        form.setParameters(new ParameterContext[] { parameter });
        return form;
    }

    private InstanceServiceImpl service(ProcessDefinition definition) throws Exception {
        InstanceServiceImpl service = spy(new InstanceServiceImpl());
        service.definitionService = mock(DefinitionServiceUtil.class);
        service.worklistRepository = mock(WorklistRepository.class);
        when(service.definitionService.getDefinition("unsafe", null)).thenReturn(definition);
        return service;
    }

    private WorklistEntity worklist() {
        WorklistEntity worklist = new WorklistEntity();
        worklist.setInstId(101L);
        worklist.setTaskId(1L);
        worklist.setDefId("unsafe");
        worklist.setDefVerId("v1");
        worklist.setTrcTag("human");
        return worklist;
    }

    private MappingElement mapping(String source, String target) {
        MappingElement element = new MappingElement();
        ProcessVariable variable = new ProcessVariable();
        variable.setName(source);
        element.setVariable(variable);
        TextContext argument = TextContext.createInstance();
        argument.setText(target);
        element.setArgument(argument);
        return element;
    }

    private EventSynchronization sync(MappingElement mapping) {
        MappingContext context = new MappingContext();
        context.setMappingElements(new MappingElement[] { mapping });
        EventSynchronization sync = new EventSynchronization();
        sync.setMappingContext(context);
        return sync;
    }

    private TransformerMapping transformer(org.uengine.processdesigner.mapper.Transformer transformer) {
        TransformerMapping mapping = new TransformerMapping();
        mapping.setTransformer(transformer);
        return mapping;
    }
}
