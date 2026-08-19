package org.uengine.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.uengine.contexts.EventSynchronization;
import org.uengine.five.serializers.BpmnXMLParser;
import org.uengine.kernel.Activity;
import org.uengine.kernel.Evaluate;
import org.uengine.kernel.FieldDescriptor;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.URLActivity;
import org.uengine.kernel.bpmn.Event;
import org.uengine.kernel.bpmn.MessageStartEvent;
import org.uengine.kernel.bpmn.SequenceFlow;

/**
 * 계정계 샘플 앱(sample-apps/bpm-sample-app-banking)과 연동하는 프로세스 정의가
 * Kafka 이벤트 연동에 필요한 형태로 파싱되는지 검증한다.
 *
 * <ul>
 *   <li>시작 이벤트가 MessageStartEvent 로 파싱되고 eventKey/corrKey 를 갖는다</li>
 *   <li>모든 작업 노드가 URLActivity(ReceiveActivity) 이고 eventSynchronization 을 갖는다</li>
 *   <li>게이트웨이 분기에 Evaluate 조건식이 붙어있다</li>
 * </ul>
 */
public class CorebankIntegrationBpmnTest {

    /**
     * 정의 원본은 서브모듈 {@code sample-apps/bpm-sample-app-banking/bpmn/} 에 있다.
     * 배포 위치인 {@code definitions/} 는 .gitignore 대상이라 새로 클론한 저장소에는 없다.
     *
     * <p>서브모듈을 받지 않았다면({@code git submodule update --init}) 이 테스트는 건너뛴다.
     */
    private static final Path BASE =
            Paths.get("..", "sample-apps", "bpm-sample-app-banking", "bpmn");

    private ProcessDefinition parse(String fileName) throws Exception {
        Path path = BASE.resolve(fileName);
        // 서브모듈 미체크아웃 시 테스트를 실패시키지 않는다.
        assumeTrue("샘플 앱 서브모듈이 없어 건너뜁니다 (git submodule update --init): "
                + path.toAbsolutePath(), Files.exists(path));
        String xml = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return new BpmnXMLParser().parse(xml);
    }

    private Activity activity(ProcessDefinition definition, String tracingTag) {
        @SuppressWarnings("unchecked")
        Map<String, Activity> all = definition.getWholeChildActivities();
        Activity activity = all.get(tracingTag);
        assertNotNull("노드를 찾을 수 없습니다: " + tracingTag + " (파싱된 노드: " + all.keySet() + ")", activity);
        return activity;
    }

    private void assertEventSync(Activity activity, String eventType, String corrField) {
        EventSynchronization[] syncs = activity.getEventSynchronizations();
        assertTrue(activity.getTracingTag() + " 에 eventSynchronization 이 없습니다", syncs.length > 0);
        assertEquals(activity.getTracingTag() + " 의 eventType", eventType, syncs[0].getEventType());

        FieldDescriptor corrKey = Arrays.stream(syncs[0].getAttributes())
                .filter(FieldDescriptor::getIsCorrKey)
                .findFirst()
                .orElse(null);
        assertNotNull(activity.getTracingTag() + " 에 correlation key 속성이 없습니다", corrKey);
        assertEquals(corrField, corrKey.getName());
    }

    private void assertCondition(ProcessDefinition definition, String sourceRef, String targetRef,
                                 String key, String value) {
        SequenceFlow flow = definition.getSequenceFlows().stream()
                .filter(f -> sourceRef.equals(f.getSourceRef()) && targetRef.equals(f.getTargetRef()))
                .findFirst()
                .orElse(null);
        assertNotNull("시퀀스 플로우 없음: " + sourceRef + " -> " + targetRef, flow);
        assertTrue("조건식이 Evaluate 가 아닙니다: " + sourceRef + " -> " + targetRef,
                flow.getCondition() instanceof Evaluate);
        Evaluate evaluate = (Evaluate) flow.getCondition();
        assertEquals(key, evaluate.getKey());
        assertEquals(value, evaluate.getValue());
    }

    @Test
    public void 일반계좌신규_이벤트연동_설정이_파싱된다() throws Exception {
        ProcessDefinition definition = parse("일반계좌신규.bpmn");

        Activity start = activity(definition, "StartEvent_1");
        assertTrue("시작 이벤트가 MessageStartEvent 여야 합니다: " + start.getClass(),
                start instanceof MessageStartEvent);
        assertEquals("AccountOpeningRequested", ((Event) start).getEventKey());
        assertEventSync(start, "AccountOpeningRequested", "applicationNo");

        List<String[]> nodes = Arrays.asList(
                new String[]{"UserTask_1", "ConsultationCompleted"},
                new String[]{"Activity_0q2ply9", "DocumentsCollected"},
                new String[]{"Activity_0dd8gbv", "DocumentsScanned"},
                new String[]{"Activity_1rnmz3z", "IdentityVerified"},
                new String[]{"Activity_1nu7hzx", "PrivacyConsentSigned"},
                new String[]{"Activity_0rv2p9p", "CustomerRegistered"},
                new String[]{"Activity_0y0af8u", "AccountOpened"},
                new String[]{"Activity_151gqj2", "PassbookIssued"},
                new String[]{"Activity_0mc1sqs", "MobileAuthConfirmed"});

        for (String[] node : nodes) {
            Activity activity = activity(definition, node[0]);
            assertTrue(node[0] + " 는 URLActivity 여야 합니다: " + activity.getClass(),
                    activity instanceof URLActivity);
            assertEventSync(activity, node[1], "applicationNo");
        }

        assertCondition(definition, "Gateway_17r3n8x", "Activity_0rv2p9p", "NewCustomer", "Y");
        assertCondition(definition, "Gateway_17r3n8x", "Activity_0y0af8u", "NewCustomer", "N");
    }

    @Test
    public void 예금잔액통보_이벤트연동_설정이_파싱된다() throws Exception {
        ProcessDefinition definition = parse("예금잔액통보.bpmn");

        Activity start = activity(definition, "StartEvent_Notice");
        assertTrue("시작 이벤트가 MessageStartEvent 여야 합니다: " + start.getClass(),
                start instanceof MessageStartEvent);
        assertEquals("BalanceNoticeScheduled", ((Event) start).getEventKey());
        assertEventSync(start, "BalanceNoticeScheduled", "noticeNo");

        List<String[]> nodes = Arrays.asList(
                new String[]{"Activity_PostTargets", "NoticeTargetsPosted"},
                new String[]{"Activity_1hibq4b", "NoticeTargetsPrinted"},
                new String[]{"Activity_1neamk4", "TargetsReviewed"},
                new String[]{"Activity_0w2f0u4", "BulkLedgerArchived"},
                new String[]{"Activity_0if7ufl", "BulkDmDispatched"},
                new String[]{"Activity_1gbb0jt", "IndividualLedgerArchived"},
                new String[]{"Activity_0i7p58v", "IndividualDmDispatched"},
                new String[]{"Activity_17h11l8", "DmReceiptConfirmed"},
                new String[]{"Activity_1m01h6k", "DmReturnReviewed"},
                new String[]{"Activity_0o195yo", "SupervisorApproved"},
                new String[]{"Activity_04nmmmh", "BalanceDiscrepancyReported"},
                new String[]{"Activity_1gdne6q", "DiscrepancyResolved"});

        for (String[] node : nodes) {
            Activity activity = activity(definition, node[0]);
            assertTrue(node[0] + " 는 URLActivity 여야 합니다: " + activity.getClass(),
                    activity instanceof URLActivity);
            assertEventSync(activity, node[1], "noticeNo");
        }

        assertCondition(definition, "Gateway_021de48", "Activity_1hibq4b", "BulkDispatch", "Y");
        assertCondition(definition, "Gateway_021de48", "Activity_1gbb0jt", "BulkDispatch", "N");
        assertCondition(definition, "Gateway_136sdqh", "Activity_1m01h6k", "DmReturned", "Y");
        assertCondition(definition, "Gateway_136sdqh", "Activity_17h11l8", "DmReturned", "N");
        assertCondition(definition, "Gateway_1lr8r88", "Activity_04nmmmh", "BalanceMismatch", "Y");
        assertCondition(definition, "Gateway_1lr8r88", "Event_0l1v4f0", "BalanceMismatch", "N");
    }
}
