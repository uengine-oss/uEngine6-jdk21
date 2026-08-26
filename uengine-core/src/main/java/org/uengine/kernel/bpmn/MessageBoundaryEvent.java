package org.uengine.kernel.bpmn;

import java.util.Map;

import org.uengine.kernel.Activity;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ValidationContext;

/** Interrupting BPMN message boundary event. */
public class MessageBoundaryEvent extends CatchingRestMessageEvent {

    public MessageBoundaryEvent() {
        setEventType(Event.BOUNDARY_EVENT);
    }

    @Override
    public boolean onMessage(ProcessInstance instance, Object payload) throws Exception {
        if (getAttachedToRef() != null && isCancelActivity()) {
            Activity attachedActivity = getProcessDefinition().getActivity(getAttachedToRef());
            if (attachedActivity != null) {
                attachedActivity.stop(instance, Activity.STATUS_CANCELLED);
            }
        }
        return super.onMessage(instance, payload);
    }

    @Override
    public ValidationContext validate(Map options) {
        ValidationContext context = new ValidationContext();
        if (getOutgoingSequenceFlows().isEmpty()) {
            context.add("해당 이벤트에서 나가는 시퀀스 플로우가 존재하지 않습니다.");
        }
        return context;
    }
}
