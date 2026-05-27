package org.uengine.test;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.uengine.contexts.EventSynchronization;
import org.uengine.contexts.UserContext;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.InstanceResource;
import org.uengine.five.dto.Message;
import org.uengine.five.dto.ProcessExecutionCommand;
import org.uengine.five.dto.RoleMapping;
import org.uengine.five.dto.WorkItemResource;
import org.uengine.five.overriding.EventMappingDeployFilter;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.overriding.SpringComponentFactory;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.DefinitionServiceUtil;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.kernel.Activity;
import org.uengine.kernel.FieldDescriptor;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ReceiveActivity;
import org.uengine.kernel.bpmn.CallActivity;
import org.uengine.modeling.resource.ContainerResource;
import org.uengine.modeling.resource.DefaultResource;
import org.uengine.modeling.resource.IResource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest(classes = ProcessServiceApplication.class)
public class InstanceServiceImplTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private DefinitionServiceUtil definitionService;

        @InjectMocks
        @Autowired
        private InstanceServiceImpl instanceService;

        @Autowired
        private WorklistRepository worklistRepository;

        @BeforeEach
        public void setup() {
                ProcessServiceApplication.applicationContext = applicationContext;
                GlobalContext.setComponentFactory(new SpringComponentFactory());

                UserContext.getThreadLocalInstance().setUserId("initiator@uengine.org");
                UserContext.getThreadLocalInstance().setScopes(new ArrayList<String>());
                UserContext.getThreadLocalInstance().getScopes().add("manager");
        }

        @Test
        public void testRunDefinition() throws Exception {
                ProcessExecutionCommand command = new ProcessExecutionCommand();
                command.setProcessDefinitionId("sales/simpleProcess");
                // command.setProcessDefinitionId("sales/고장신고서브프로세스.bpmn");
                command.setSimulation(false);

                RoleMapping roleMapping = new RoleMapping();
                roleMapping.setName("initiator");
                roleMapping.setEndpoints(new String[] { "initiator@uengine.org" });
                roleMapping.setResourceNames(new String[] { "Initiator" });
                command.setRoleMappings(new RoleMapping[] { roleMapping });

                InstanceResource instanceResource = instanceService.start(command);
                String instanceId = instanceResource.getInstanceId();

                Map<String, Object> variables = instanceService.getProcessVariables(instanceId);

                System.out.println(variables);// Add assertions to verify the behavior

                ProcessInstance instance = instanceService.getProcessInstanceLocal(instanceId);

                assertEquals("Task_b should be in RUNNING status", Activity.STATUS_RUNNING,
                                instance.getStatus("Activity_0opnhve"));

                HumanActivity taskB = (HumanActivity) instance.getProcessDefinition().getActivity("Activity_0opnhve");
                String[] taskIds = taskB.getTaskIds(instance);
                boolean worklistExists = false;

                // for (String taskId : taskIds) {
                // Optional<WorklistEntity> worklistEntity =
                // worklistRepository.findById(Long.parseLong(taskId));
                // if (worklistEntity.isPresent()) {
                // worklistExists = true;
                // break;
                // }

                List<WorklistEntity> worklistEntity = worklistRepository.findToDo();
                if (worklistEntity.size() > 0) {
                        worklistExists = true;
                        // break;
                }
                // }
                assertTrue("Worklist for Task_b should exist", worklistExists);

                UserContext.getThreadLocalInstance().setUserId("otheruser@uengine.org");
                UserContext.getThreadLocalInstance().setScopes(new ArrayList<String>());
                UserContext.getThreadLocalInstance().getScopes().add("worker");
                worklistEntity = worklistRepository.findToDo();
                assertTrue("Worklist for otheruser@uengine.org should be empty", worklistEntity.size() == 0);

                String symptom = "An example error symptom description";
                WorkItemResource workItemResource = new WorkItemResource();
                Map<String, Object> parameterValues = new HashMap<>();
                parameterValues.put("symptom", symptom);
                parameterValues.put("troubleType", "sw");
                instance.set("symptom", symptom);
                workItemResource.setParameterValues(parameterValues);
                instanceService.putWorkItemComplete(taskIds[0], workItemResource, "false");

                // assertEquals(symptom, instance.get("", "symptom"));

                assertEquals("Task_c should be in RUNNING status", Activity.STATUS_RUNNING,
                                instance.getStatus("Activity_0g1izgt"));

                instanceService.postMessage(instanceId, new Message("Receive", null));

                assertEquals("Task_c should be in COMPLETED status", Activity.STATUS_COMPLETED,
                                instance.getStatus("Activity_0g1izgt"));
                assertNotEquals("Task_d should not be in COMPLETED status", Activity.STATUS_COMPLETED,
                                instance.getStatus("Activity_08lumh2"));

                instanceService.backToHere(instanceId, "Activity_0opnhve");

                assertEquals("Task_c should be in Ready status",
                                Activity.STATUS_READY,
                                instance.getStatus("Activity_0g1izgt"));

                parameterValues.put("troubleType", "hw");

                taskIds = taskB.getTaskIds(instance);
                instanceService.postMessage(instanceId, new Message("Receive", null));

                workItemResource.setParameterValues(parameterValues);
                instanceService.putWorkItemComplete(taskIds[0], workItemResource, "false");

                assertEquals("Task_d should be in Running status", Activity.STATUS_RUNNING,
                                instance.getStatus("Activity_08lumh2"));

                CallActivity callActivity = (CallActivity) instance.getProcessDefinition()
                                .getActivity("Activity_08lumh2");
                Vector ids = callActivity.getSubprocessIds(instance);

                ProcessInstance subProcessInstance = instanceService.getProcessInstanceLocal(ids.get(0).toString());
                // taskIds =
                // ((HumanActivity)subProcessInstance.getProcessDefinition().getActivity("Task_b")).getTaskIds(subProcessInstance);

                // assertEquals(subProcessInstance.getRoleMapping("initiator").getEndpoint(),
                // instance.getRoleMapping("initiator").getEndpoint());

                System.out.println(ids);

                // assertEquals("Process instance should be completed",
                // Activity.STATUS_COMPLETED, instance.getStatus());

        }

        // @Test
        // public void testBpmnDefinition() throws Exception {
        // ProcessExecutionCommand command = new ProcessExecutionCommand();
        // command.setProcessDefinitionId("sales/simpleProcess");
        // command.setSimulation(false);

        // RoleMapping roleMapping = new RoleMapping();
        // roleMapping.setName("initiator");
        // roleMapping.setEndpoints(new String[] { "initiator@uengine.org" });
        // roleMapping.setResourceNames(new String[] { "Initiator" });
        // command.setRoleMappings(new RoleMapping[] { roleMapping });

        // InstanceResource instanceResource = instanceService.start(command);
        // String instanceId = instanceResource.getInstanceId();

        // Map<String, Object> variables =
        // instanceService.getProcessVariables(instanceId);

        // System.out.println(variables);// Add assertions to verify the behavior

        // ProcessInstance instance =
        // instanceService.getProcessInstanceLocal(instanceId);

        // assertEquals("Task_b should be in RUNNING status", Activity.STATUS_RUNNING,
        // instance.getStatus("Task_b"));

        // HumanActivity taskB = (HumanActivity)
        // instance.getProcessDefinition().getActivity("Activity_0opnhve");
        // String[] taskIds = taskB.getTaskIds(instance);
        // boolean worklistExists = false;
        // for (String taskId : taskIds) {
        // Optional<WorklistEntity> worklistEntity =
        // worklistRepository.findById(Long.parseLong(taskId));
        // if (worklistEntity.isPresent()) {
        // worklistExists = true;
        // break;
        // }
        // }
        // assertTrue("Worklist for Task_b should exist", worklistExists);

        // String symptom = "An example error symptom description";
        // WorkItemResource workItemResource = new WorkItemResource();
        // Map<String, Object> parameterValues = new HashMap<>();
        // parameterValues.put("symptom", symptom);
        // parameterValues.put("troubleType", "sw");

        // workItemResource.setParameterValues(parameterValues);
        // instanceService.putWorkItem(taskIds[0], workItemResource);

        // assertEquals(symptom, instance.get("", "symptom"));

        // assertEquals("Task_c should be in RUNNING status", Activity.STATUS_RUNNING,
        // instance.getStatus("Activity_0g1izgt"));

        // instanceService.postMessage(instanceId, new Message("Receive", null));

        // assertEquals("Task_c should be in COMPLETED status",
        // Activity.STATUS_COMPLETED,
        // instance.getStatus("Activity_0g1izgt"));
        // assertNotEquals("Task_d should not be in COMPLETED status",
        // Activity.STATUS_COMPLETED,
        // instance.getStatus("Activity_08lumh2"));

        // instanceService.backToHere(instanceId, "Activity_0opnhve");

        // assertEquals("Task_c should be in Ready status",
        // Activity.STATUS_READY,
        // instance.getStatus("Activity_0g1izgt"));

        // parameterValues.put("troubleType", "hw");

        // taskIds = taskB.getTaskIds(instance);

        // workItemResource.setParameterValues(parameterValues);
        // instanceService.putWorkItem(taskIds[0], workItemResource);

        // assertEquals("Task_d should be in Running status", Activity.STATUS_RUNNING,
        // instance.getStatus("Activity_08lumh2"));

        // CallActivity callActivity = (CallActivity) instance.getProcessDefinition()
        // .getActivity("Activity_08lumh2");
        // Vector ids = callActivity.getSubprocessIds(instance);

        // ProcessInstance subProcessInstance =
        // instanceService.getProcessInstanceLocal(ids.get(0).toString());
        // // taskIds =
        // //
        // ((HumanActivity)subProcessInstance.getProcessDefinition().getActivity("Task_b")).getTaskIds(subProcessInstance);

        // assertEquals(subProcessInstance.getRoleMapping("initiator").getEndpoint(),
        // instance.getRoleMapping("initiator").getEndpoint());

        // System.out.println(ids);

        // // assertEquals("Process instance should be completed",
        // // Activity.STATUS_COMPLETED, instance.getStatus());

        // }

        // @Test
        // public void testProcessExecutionSteps() throws Exception {
        // // 프로세스 정의 ID 설정
        // String processDefinitionId = "test/고장테스트";
        // List<String> expectationStatus = new ArrayList<>(
        // Arrays.asList(DefaultWorkList.WORKITEM_STATUS_COMPLETED,
        // DefaultWorkList.WORKITEM_STATUS_COMPLETED,
        // DefaultWorkList.WORKITEM_STATUS_COMPLETED,
        // DefaultWorkList.WORKITEM_STATUS_NEW));

        // // 프로세스 실행 명령 생성
        // ProcessExecutionCommand command = new ProcessExecutionCommand();
        // command.setProcessDefinitionId(processDefinitionId);
        // command.setSimulation(true);

        // // 프로세스 인스턴스 시작
        // InstanceResource instanceResource = instanceService.start(command);
        // String instanceId = instanceResource.getInstanceId();

        // // 프로세스 인스턴스 상태 확인
        // assertEquals("Process instance should be in RUNNING status",
        // Activity.STATUS_RUNNING,
        // instanceService.getInstance(instanceId).getStatus());
        // File folder = new File("../test/" + processDefinitionId);
        // File[] files = folder.listFiles();
        // // 작업 항목 가져오기
        // Map<String, Object> workItems = instanceService
        // .testList(files);
        // assertNotNull("Work items should not be null", workItems);

        // // 작업 항목 처리 반복
        // for (String taskId : workItems.keySet()) {
        // WorkItemResource workItemResource = new WorkItemResource();
        // Map<String, Object> parameterValues = new HashMap<>();
        // // 첫 번째 workItem의 내용을 parameterValues에 추가
        // String firstWorkItemContent = (String) workItems.get(taskId);
        // ObjectMapper objectMapper = new ObjectMapper();
        // List<Map<String, Object>> firstWorkItemList =
        // objectMapper.readValue(firstWorkItemContent,
        // List.class);
        // // 현재 실행 중인 작업 항목 ID 가져오기
        // List<WorklistEntity> runningTasks =
        // instanceService.getRunningTaskId(instanceId).getBody();
        // assertNotNull("Running tasks should not be null", runningTasks);
        // assertFalse("There should be at least one running task",
        // runningTasks.isEmpty());

        // // 첫 번째 실행 중인 작업 항목 ID를 taskId로 설정
        // taskId = runningTasks.get(0).getTaskId().toString();
        // String trcTag = runningTasks.get(0).getTrcTag().toString();
        // if (!trcTag.equals(taskId))
        // continue;

        // if (!firstWorkItemList.isEmpty()) {
        // Map<String, Object> firstWorkItemMap = firstWorkItemList.get(0);
        // parameterValues.putAll(firstWorkItemMap);
        // }
        // workItemResource.setParameterValues(parameterValues);
        // instanceService.putWorkItemComplete(taskId, workItemResource, "true");

        // // 메시지 전송
        // instanceService.postMessage(instanceId, new Message("Receive", null));

        // // 작업 항목 상태 확인
        // // assertEquals("Task should be in COMPLETED status",
        // Activity.STATUS_COMPLETED,
        // // instanceService.getStatus(taskId));
        // }

        // // 프로세스 인스턴스 완료 확인
        // List<WorklistEntity> worklist =
        // worklistRepository.findWorkListByInstId(Long.parseLong(instanceId));

        // for (int i = 0; i < worklist.size(); i++) {
        // assertEquals("Work item status should match the expected status",
        // expectationStatus.get(i), worklist.get(i).getStatus());
        // }
        // }

        @Test
        public void collectAllBpmnDefinitionPaths_recursesAndFiltersUnsupportedResources() throws Exception {
                TestableInstanceServiceImpl service = new TestableInstanceServiceImpl();
                service.resourcesByBasePath.put("", List.of(
                                new ContainerResource("definitions/sales"),
                                new ContainerResource("definitions/archive"),
                                new ContainerResource("definitions/businessRules"),
                                new DefaultResource("definitions/root.bpmn"),
                                new DefaultResource("definitions/map.json")));
                service.resourcesByBasePath.put("sales", List.of(
                                new DefaultResource("definitions/sales/order.bpmn"),
                                new ContainerResource("definitions/sales/sub")));
                service.resourcesByBasePath.put("sales/sub", List.of(
                                new DefaultResource("definitions/sales/sub/claim.bpmn"),
                                new DefaultResource("definitions/sales/sub/readme.txt")));

                List<String> actual = service.collectAllBpmnDefinitionPaths();

                assertIterableEquals(List.of(
                                "root.bpmn",
                                "sales/order.bpmn",
                                "sales/sub/claim.bpmn"), actual);
        }

        @Test
        public void syncAllDefinitionChanges_collectsFailuresWithoutClearingByDefault() throws Exception {
                TestableInstanceServiceImpl service = new TestableInstanceServiceImpl();
                EventMappingRepository eventMappingRepository = org.mockito.Mockito.mock(EventMappingRepository.class);
                ReflectionTestUtils.setField(service, "eventMappingRepository", eventMappingRepository);
                service.discoveredPaths = List.of("good-a.bpmn", "bad.bpmn", "good-b.bpmn");
                service.failOnLoadPath = "bad.bpmn";

                Map<String, Object> result = service.syncAllDefinitionChanges(false);

                assertEquals(3, result.get("totalDefinitionCount"));
                assertEquals(2, result.get("successCount"));
                assertEquals(1, result.get("failedCount"));
                assertIterableEquals(List.of("bad.bpmn"), castList(result.get("failedPaths")));
                assertIterableEquals(List.of("good-a.bpmn", "good-b.bpmn"), service.deployedPaths);
                org.mockito.Mockito.verify(eventMappingRepository, org.mockito.Mockito.never()).deleteAll();
        }

        @Test
        public void syncAllDefinitionChanges_clearsEventMappingsWhenRequested() throws Exception {
                TestableInstanceServiceImpl service = new TestableInstanceServiceImpl();
                EventMappingRepository eventMappingRepository = org.mockito.Mockito.mock(EventMappingRepository.class);
                ReflectionTestUtils.setField(service, "eventMappingRepository", eventMappingRepository);
                service.discoveredPaths = List.of("only.bpmn");

                Map<String, Object> result = service.syncAllDefinitionChanges(true);

                assertEquals(true, result.get("clearAllEventMappings"));
                org.mockito.Mockito.verify(eventMappingRepository).deleteAll();
        }

        @Test
        public void eventMappingDeployFilter_logsRegisteredEventMappingsWithDefinitionAndEventType() throws Exception {
                EventMappingDeployFilter filter = new EventMappingDeployFilter();
                EventMappingRepository eventMappingRepository = org.mockito.Mockito.mock(EventMappingRepository.class);
                ReflectionTestUtils.setField(filter, "eventMappingRepository", eventMappingRepository);

                ReceiveActivity receiveActivity = org.mockito.Mockito.mock(ReceiveActivity.class);
                EventSynchronization synchronization = new EventSynchronization();
                synchronization.setEventType("OrderCreated");
                FieldDescriptor correlationKey = new FieldDescriptor();
                correlationKey.setName("orderId");
                correlationKey.setIsCorrKey(true);
                synchronization.setAttributes(new FieldDescriptor[] { correlationKey });

                org.mockito.Mockito.when(receiveActivity.getEventSynchronizations())
                                .thenReturn(new EventSynchronization[] { synchronization });
                org.mockito.Mockito.when(receiveActivity.getTracingTag()).thenReturn("Activity_receiveOrder");

                org.uengine.kernel.ProcessDefinition definition = org.mockito.Mockito
                                .mock(org.uengine.kernel.ProcessDefinition.class);
                org.mockito.Mockito.when(definition.getId()).thenReturn("sales/order-process.bpmn");
                org.mockito.Mockito.when(definition.getStartActivities()).thenReturn(List.of());
                org.mockito.Mockito.when(definition.getEvents()).thenReturn(List.of());
                org.mockito.Mockito.when(definition.getChildActivities()).thenReturn(List.of(receiveActivity));

                Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(EventMappingDeployFilter.class);
                Level previousLevel = logger.getLevel();
                ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
                listAppender.start();
                logger.addAppender(listAppender);
                logger.setLevel(Level.INFO);

                try {
                        filter.beforeDeploy(definition, null, "sales/order-process.bpmn", true);
                } finally {
                        logger.detachAppender(listAppender);
                        logger.setLevel(previousLevel);
                }

                org.mockito.Mockito.verify(eventMappingRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                        "sales/order-process.bpmn".equals(entity.getDefinitionId())
                                && "OrderCreated".equals(entity.getEventName())
                                && "orderId".equals(entity.getCorrelationKey())
                                && "Activity_receiveOrder".equals(entity.getTracingTag())));

                List<String> messages = listAppender.list.stream()
                                .map(ILoggingEvent::getFormattedMessage)
                                .toList();

                for (String message : messages) {
                        System.out.println("[event-mapping-test-log] " + message);
                }

                assertTrue(messages.stream().anyMatch(message ->
                        message.contains("sales/order-process.bpmn")
                                && message.contains("OrderCreated")
                                && message.contains("Activity_receiveOrder")));
        }

        @SuppressWarnings("unchecked")
        private List<String> castList(Object value) {
                return (List<String>) value;
        }

        private static class TestableInstanceServiceImpl extends InstanceServiceImpl {
                final Map<String, List<IResource>> resourcesByBasePath = new LinkedHashMap<>();
                final List<String> deployedPaths = new ArrayList<>();
                List<String> discoveredPaths;
                String failOnLoadPath;

                @Override
                protected List<IResource> listDefinitionResources(String basePath) {
                        return resourcesByBasePath.getOrDefault(basePath == null ? "" : basePath, List.of());
                }

                @Override
                protected List<String> collectAllBpmnDefinitionPaths() throws Exception {
                        if (discoveredPaths != null) {
                                return discoveredPaths;
                        }
                        return super.collectAllBpmnDefinitionPaths();
                }

                @Override
                protected org.uengine.kernel.ProcessDefinition loadDefinitionForSync(String definitionPath) throws Exception {
                        if (definitionPath.equals(failOnLoadPath)) {
                                throw new IllegalStateException("load failure");
                        }
                        return org.mockito.Mockito.mock(org.uengine.kernel.ProcessDefinition.class);
                }

                @Override
                protected void invokeDeployFiltersForSync(org.uengine.kernel.ProcessDefinition definitionDeployed,
                                String path) {
                        deployedPaths.add(path);
                }
        }
}