package org.uengine.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
import org.uengine.five.serializers.BpmnXMLParser;
import org.uengine.kernel.Activity;
import org.uengine.kernel.Evaluate;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.bpmn.SequenceFlow;

public class SdsBmtCreditReviewSubBpmnTest {

    @Test
    public void mainProcessesStopAtFirstUserTaskAfterAutomaticTasks() throws Exception {
        String[] paths = new String[] {
                "CorporateCardIssue/CorporateCardIssue.bpmn",
                "DepositBalanceNotice/DepositBalanceNotice.bpmn",
                "ExportBillPurchase/ExportBillPurchase.bpmn",
                "HomeMortgageLoan/HomeMortgageLoan.bpmn",
                "Integrated_CorporateLoan/Integrated_CorporateLoan.bpmn",
                "NewDepositAccount/NewDepositAccount.bpmn"
        };

        for (String path : paths) {
            ProcessDefinition processDefinition = parseDefinition(path);
            ProcessInstance instance = processDefinition.createInstance();
            instance.putRoleMapping("user", "hong");

            instance.execute();

            assertNotNull(path + " must have a running activity", instance.getCurrentRunningActivity());
            Activity runningActivity = instance.getCurrentRunningActivity().getActivity();
            assertTrue(path + " must stop at a user task, but was " + runningActivity.getClass().getName(),
                    runningActivity instanceof HumanActivity);
            assertEquals(path + " running user task must stay Running",
                    Activity.STATUS_RUNNING, runningActivity.getStatus(instance));
        }
    }

    @Test
    public void homeMortgageLoanOwnershipConsentGatewayTargetsAreBound() throws Exception {
        ProcessDefinition processDefinition = parseDefinition("HomeMortgageLoan/HomeMortgageLoan.bpmn");

        SequenceFlow yFlow = findFlow(processDefinition, "Flow_1b7o02y");
        SequenceFlow nFlow = findFlow(processDefinition, "Flow_1s0oqaj");

        assertCondition(yFlow, "Gateway_0c9w4dp_주택소유수_조회도의여부", "==", "Y");
        assertCondition(nFlow, "Gateway_0c9w4dp_주택소유수_조회도의여부", "==", "N");

        assertNotNull("Y branch target activity must be registered", processDefinition.getActivity(yFlow.getTargetRef()));
        assertNotNull("N branch target activity must be registered", processDefinition.getActivity(nFlow.getTargetRef()));
        assertNotNull("Y branch targetActivity must be bound", yFlow.getTargetActivity());
        assertNotNull("N branch targetActivity must be bound", nFlow.getTargetActivity());
    }

    @Test
    public void creditReviewSubGatewayConditionIsParsed() throws Exception {
        ProcessDefinition processDefinition = parseDefinition("callActivity/CreditReview_Sub.bpmn");

        SequenceFlow nFlow = findFlow(processDefinition, "Flow_11a72sd");
        SequenceFlow yFlow = findFlow(processDefinition, "Flow_176cxus");

        assertCondition(nFlow, "Gateway_1l81ivs_서류충족여부", "==", "N");
        assertCondition(yFlow, "Gateway_1l81ivs_서류충족여부", "==", "Y");

        Activity nTarget = processDefinition.getActivity(nFlow.getTargetRef());
        Activity yTarget = processDefinition.getActivity(yFlow.getTargetRef());

        assertTrue("N branch must stop at a user task", nTarget instanceof HumanActivity);
        assertEquals("보완요청", nTarget.getName());
        assertEquals("Y branch currently enters an automatic task", "여신심사위원회 개의", yTarget.getName());
    }

    private static void assertCondition(SequenceFlow flow, String key, String operator, String value) {
        assertNotNull("condition must be parsed for " + flow.getTracingTag(), flow.getCondition());
        assertTrue("condition must be Evaluate", flow.getCondition() instanceof Evaluate);
        Evaluate evaluate = (Evaluate) flow.getCondition();
        assertEquals(key, evaluate.getKey());
        assertEquals(operator, evaluate.getCondition());
        assertEquals(value, evaluate.getValue());
    }

    private static SequenceFlow findFlow(ProcessDefinition processDefinition, String tracingTag) {
        return processDefinition.getSequenceFlows().stream()
                .filter(flow -> tracingTag.equals(flow.getTracingTag()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing sequence flow: " + tracingTag));
    }

    private static ProcessDefinition parseDefinition(String relativePath) throws Exception {
        Path bpmn = findSdsRoot().resolve(
                "delivery/docker-bmt-test-package-20260817/process-assets/definitions/" + relativePath);
        String xml = Files.readString(bpmn, StandardCharsets.UTF_8);
        return new BpmnXMLParser().parse(xml);
    }

    private static Path findSdsRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("delivery/docker-bmt-test-package-20260817"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Cannot find SDS repository root from user.dir");
    }
}
