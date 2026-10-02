package org.uengine.kernel.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import org.uengine.kernel.*;
import org.uengine.kernel.bpmn.*;

public class BackToHereResumeTest extends UEngineTest {
    private final int expectedStarts = Integer.getInteger("backtohere.expectedStarts", 1);

    private static class Probe extends ReceiveActivity {
        int starts;
        int attachedEvents;
        Probe(String tag) {
            setTracingTag(tag);
            setMessage(tag + "Message");
        }
        @Override protected void executeActivity(ProcessInstance instance) throws Exception {
            starts++;
            super.executeActivity(instance);
        }
        @Override protected void beforeExecute(ProcessInstance instance) throws Exception {
            if (instance.isRunning(getTracingTag())) System.out.println("COMPARE runningTarget=" + getTracingTag());
            super.beforeExecute(instance);
        }
        @Override public void executeAttachedEvent(ProcessInstance instance) throws Exception {
            attachedEvents++;
            super.executeAttachedEvent(instance);
        }
    }

    private static class Instance extends DefaultProcessInstance {
        int warnings;
        Instance(ProcessDefinition definition) throws Exception { super(definition, "resumeProbe", null); }
        @Override public void addDebugInfo(Object message) {
            if (message instanceof Throwable && ((Throwable) message).getMessage() != null
                    && ((Throwable) message).getMessage().contains("more than once")) {
                warnings++;
                System.out.println("COMPARE warning=" + ((Throwable) message).getMessage());
                for (StackTraceElement frame : ((Throwable) message).getStackTrace()) {
                    if (frame.getMethodName().equals("backToHere") || frame.getMethodName().equals("compensateChild")
                            || frame.getMethodName().equals("compensateToThis")) System.out.println("COMPARE origin=" + frame);
                }
            }
            super.addDebugInfo(message);
        }
    }

    private ProcessDefinition sequence(Activity first, Activity second) throws Exception {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId("resumeProbeDefinition");
        StartEvent start = new StartEvent();
        start.setTracingTag("start");
        definition.addChildActivity(start);
        definition.addChildActivity(first);
        definition.addChildActivity(second);
        definition.addSequenceFlow(new SequenceFlow("start", first.getTracingTag()));
        definition.addSequenceFlow(new SequenceFlow(first.getTracingTag(), second.getTracingTag()));
        definition.afterDeserialization();
        return definition;
    }

    public void testRepeatedReturnAndNormalCompletion() throws Exception {
        Probe first = new Probe("first");
        Probe second = new Probe("second");
        Instance instance = new Instance(sequence(first, second));
        instance.execute();
        for (int round = 1; round <= 3; round++) {
            first.fireComplete(instance);
            assertEquals(Activity.STATUS_RUNNING, second.getStatus(instance));
            int before = first.starts;
            first.backToHere(instance);
            assertEquals(expectedStarts, first.starts - before);
            assertEquals(Activity.STATUS_RUNNING, first.getStatus(instance));
            assertEquals(Activity.STATUS_COMPENSATED, second.getStatus(instance));
            assertEquals(round, first.attachedEvents);
        }
        assertEquals(3 * (expectedStarts - 1), instance.warnings);
        System.out.println("COMPARE basic rollbackStarts=" + expectedStarts + " warnings=" + instance.warnings);
        first.fireComplete(instance);
        second.fireComplete(instance);
        instance.getProcessTransactionContext().commit();
        assertEquals(Activity.STATUS_COMPLETED, instance.getStatus());
    }

    public void testDefaultCompensationStillResumesOnce() throws Exception {
        Probe first = new Probe("first");
        Probe second = new Probe("second");
        Instance instance = new Instance(sequence(first, second));
        instance.execute();
        first.fireComplete(instance);
        int before = first.starts;
        first.compensateToThis(instance);
        assertEquals(1, first.starts - before);
        assertEquals(0, instance.warnings);
        assertEquals(Activity.STATUS_RUNNING, first.getStatus(instance));
        assertEquals(Activity.STATUS_COMPENSATED, second.getStatus(instance));
    }

    public void testReturnInsideCompletedEmbeddedSubprocess() throws Exception {
        SubProcess embedded = new SubProcess();
        embedded.setTracingTag("embedded");
        Probe inside = new Probe("inside");
        EndEvent end = new EndEvent();
        end.setTracingTag("end");
        embedded.addChildActivity(inside);
        embedded.addChildActivity(end);
        embedded.addSequenceFlow(new SequenceFlow("inside", "end"));
        Probe next = new Probe("next");
        Instance instance = new Instance(sequence(embedded, next));
        instance.execute();
        inside.fireComplete(instance);
        instance.getProcessTransactionContext().commit();
        assertEquals(Activity.STATUS_COMPLETED, embedded.getStatus(instance));
        assertEquals(Activity.STATUS_RUNNING, next.getStatus(instance));
        int before = inside.starts;
        int beforeWarnings = instance.warnings;
        inside.backToHere(instance);
        assertEquals(expectedStarts, inside.starts - before);
        assertEquals(Activity.STATUS_RUNNING, inside.getStatus(instance));
        assertEquals(Activity.STATUS_RUNNING, embedded.getStatus(instance));
        // Existing behavior: returning inside a completed embedded scope leaves its successor running.
        assertEquals(Activity.STATUS_RUNNING, next.getStatus(instance));
        assertEquals(expectedStarts - 1, instance.warnings - beforeWarnings);
        System.out.println("COMPARE embedded rollbackStarts=" + (inside.starts - before)
                + " rollbackWarnings=" + (instance.warnings - beforeWarnings) + " initialWarnings=" + beforeWarnings);
        inside.fireComplete(instance);
        instance.getProcessTransactionContext().commit();
        assertEquals(Activity.STATUS_RUNNING, next.getStatus(instance));
    }

    public void testReturnBeforeRunningCallCompensatesChild() throws Exception {
        Probe childTask = new Probe("childTask");
        Probe childNext = new Probe("childNext");
        Instance child = new Instance(sequence(childTask, childNext));
        child.execute();
        CallActivity call = new CallActivity() {
            @Override protected void executeActivity(ProcessInstance instance) { }
            @Override public Vector getSubProcesses(ProcessInstance instance) {
                Vector<ProcessInstance> children = new Vector<>();
                children.add(child);
                return children;
            }
        };
        call.setTracingTag("call");
        Probe first = new Probe("first");
        Instance parent = new Instance(sequence(first, call));
        parent.execute();
        first.fireComplete(parent);
        int before = first.starts;
        first.backToHere(parent);
        assertEquals(expectedStarts, first.starts - before);
        assertEquals(Activity.STATUS_RUNNING, first.getStatus(parent));
        assertEquals(Activity.STATUS_COMPENSATED, call.getStatus(parent));
        assertEquals(Activity.STATUS_COMPENSATED, child.getStatus());
        assertEquals(Activity.STATUS_COMPENSATED, childTask.getStatus(child));
        assertEquals(expectedStarts - 1, parent.warnings);
        System.out.println("COMPARE call rollbackStarts=" + (first.starts - before) + " child=" + child.getStatus());
    }

    public void testReturnedFilterOrder() throws Exception {
        List<String> events = new ArrayList<>();
        Probe first = new Probe("first");
        Probe second = new Probe("second");
        ProcessDefinition definition = sequence(first, second);
        definition.setActivityFilters(new ActivityFilter[] { new SensitiveActivityFilter() {
            public void onEvent(Activity a, ProcessInstance i, String name, Object value) throws Exception {
                if (a == first && "returned".equals(name)) events.add("returned:" + a.getStatus(i));
            }
            public void beforeExecute(Activity a, ProcessInstance i) { if (a == first) events.add("execute"); }
            public void afterExecute(Activity a, ProcessInstance i) { }
            public void afterComplete(Activity a, ProcessInstance i) { }
            public void afterFault(Activity a, ProcessInstance i, FaultContext f) { }
            public void onPropertyChange(Activity a, ProcessInstance i, String n, Object v) { }
            public void onDeploy(ProcessDefinition d) { }
        } });
        Instance instance = new Instance(definition);
        instance.execute();
        first.fireComplete(instance);
        events.clear();
        first.backToHere(instance);
        System.out.println("COMPARE filter=" + events);
        if (expectedStarts == 2) assertEquals("[execute, returned:Running, execute]", events.toString());
        else assertEquals("[returned:Compensated, execute]", events.toString());
    }

    public void testCompensationBoundaryEventIsPreserved() throws Exception {
        Probe first = new Probe("first");
        Probe second = new Probe("second");
        ProcessDefinition definition = sequence(first, second);
        int[] events = { 0 };
        CompensateEvent boundary = new CompensateEvent() {
            @Override public boolean onMessage(ProcessInstance instance, Object payload) throws Exception {
                events[0]++;
                return super.onMessage(instance, payload);
            }
        };
        boundary.setTracingTag("boundary");
        boundary.setAttachedToRef("first");
        definition.addChildActivity(boundary);
        definition.afterDeserialization();
        Instance instance = new Instance(definition);
        instance.execute();
        first.fireComplete(instance);
        first.backToHere(instance);
        assertEquals(1, events[0]);
        assertEquals(Activity.STATUS_RUNNING, first.getStatus(instance));
        System.out.println("COMPARE boundary handlerCalls=" + events[0]);
    }

    public void testReturnToCallCreatesOneReplacementChild() throws Exception {
        List<ProcessInstance> children = new ArrayList<>();
        CallActivity call = new CallActivity() {
            @Override protected ProcessInstance initiateSubProcess(String id, ProcessInstance parent,
                    RoleMapping role, java.io.Serializable value, boolean connected, int index) throws Exception {
                Probe first = new Probe("childFirst");
                Probe second = new Probe("childSecond");
                java.util.Map<String, Object> options = new java.util.HashMap<>();
                options.put("ptc", parent.getProcessTransactionContext());
                options.put("isSubProcess", "yes");
                options.put(DefaultProcessInstance.RETURNING_PROCESS, parent.getInstanceId());
                options.put(DefaultProcessInstance.RETURNING_TRACINGTAG, getTracingTag());
                ProcessInstance child = new DefaultProcessInstance(sequence(first, second), "child" + children.size(), options);
                child.setProcessTransactionContext(parent.getProcessTransactionContext());
                parent.getProcessTransactionContext().getProcessInstancesInTransaction().put(parent.getInstanceId(), parent);
                parent.getProcessTransactionContext().getProcessInstancesInTransaction().put(child.getInstanceId(), child);
                children.add(child);
                return child;
            }
        };
        call.setTracingTag("call");
        call.setDefinitionId("testChild");
        call.setVariableBindings(new SubProcessParameterContext[0]);
        call.setRoleBindings(new RoleParameterContext[0]);
        Probe next = new Probe("next");
        Instance parent = new Instance(sequence(call, next));
        parent.execute();
        ProcessInstance oldChild = children.get(0);
        oldChild.getProcessDefinition().getActivity("childFirst").fireComplete(oldChild);
        oldChild.getProcessDefinition().getActivity("childSecond").fireComplete(oldChild);
        oldChild.getProcessTransactionContext().commit();
        assertEquals(Activity.STATUS_COMPLETED, call.getStatus(parent));
        assertEquals(Activity.STATUS_RUNNING, next.getStatus(parent));
        int before = children.size();
        call.backToHere(parent);
        assertEquals(expectedStarts, children.size() - before);
        assertEquals(Activity.STATUS_RUNNING, call.getStatus(parent));
        assertEquals(Activity.STATUS_RUNNING, children.get(children.size() - 1).getStatus());
        assertEquals(Activity.STATUS_COMPENSATED, next.getStatus(parent));
        System.out.println("COMPARE callTarget newChildren=" + (children.size() - before)
                + " warnings=" + parent.warnings);
    }

    public void testHumanWorkItemCreation() throws Exception {
        int[] starts = { 0 };
        HumanActivity first = new HumanActivity() {
            @Override protected void executeActivity(ProcessInstance instance) throws Exception {
                starts[0]++;
                super.executeActivity(instance);
            }
        };
        first.setTracingTag("human");
        first.setRole(new Role("worker"));
        Probe second = new Probe("second");
        ProcessDefinition definition = sequence(first, second);
        definition.setRoles(new Role[] { first.getRole() });
        List<String> added = new ArrayList<>();
        List<String> compensated = new ArrayList<>();
        org.uengine.webservices.worklist.DefaultWorkList worklist = new org.uengine.webservices.worklist.DefaultWorkList() {
            @Override public String addWorkItem(RoleMapping mapping, KeyedParameter[] parameters,
                    org.uengine.processmanager.TransactionContext tx) {
                String id = "task" + added.size();
                added.add(id);
                return id;
            }
            @Override public void compensateWorkItem(String id, KeyedParameter[] parameters,
                    org.uengine.processmanager.TransactionContext tx) { compensated.add(id); }
        };
        Instance instance = new Instance(definition) {
            @Override public org.uengine.webservices.worklist.WorkList getWorkList() { return worklist; }
        };
        instance.putRoleMapping("worker", "tester");
        instance.execute();
        String oldTask = first.getTaskIds(instance)[0];
        first.fireComplete(instance);
        int beforeStarts = starts[0];
        int beforeTasks = added.size();
        first.backToHere(instance);
        assertEquals(expectedStarts, starts[0] - beforeStarts);
        assertEquals(expectedStarts, added.size() - beforeTasks);
        assertTrue(compensated.contains(oldTask));
        assertEquals(1, first.getTaskIds(instance).length);
        assertFalse(oldTask.equals(first.getTaskIds(instance)[0]));
        assertEquals(Activity.STATUS_RUNNING, first.getStatus(instance));
        assertEquals(Activity.STATUS_COMPENSATED, second.getStatus(instance));
        System.out.println("COMPARE human rollbackStarts=" + (starts[0] - beforeStarts)
                + " newTasks=" + (added.size() - beforeTasks) + " warnings=" + instance.warnings);
    }
}
