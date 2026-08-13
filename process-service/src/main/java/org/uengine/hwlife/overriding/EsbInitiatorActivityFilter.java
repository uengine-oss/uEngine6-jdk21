package org.uengine.hwlife.overriding;

import java.io.Serializable;

import org.uengine.kernel.Activity;
import org.uengine.kernel.ActivityFilter;
import org.uengine.kernel.FaultContext;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;

/**
 * 신규 인스턴스 실행 시 ESB header 의 요청자(emnb)·요청기관(belnOrgnCode)을
 * {@code init_ep} / {@code init_group_cd} 에 반영한다.
 *
 * <p>{@link org.uengine.five.overriding.InstanceDataAppendingActivityFilter} 의
 * {@code ProcessInstanceHandlerFields.initialize} 보다 앞선 {@code beforeExecute} 에서
 * 채워 두어, RoleMapping 기반 초기화가 이미 값이 있으면 덮어쓰지 않도록 한다.</p>
 */
public class EsbInitiatorActivityFilter implements ActivityFilter, Serializable {

    private static final long serialVersionUID = 1L;

    // ESB header actor is request metadata, not the first unit-work assignee.
    // BPM_PROCINST init* fields are populated from the first WorkItem/RoleMapping.

    @Override
    public void beforeExecute(Activity activity, ProcessInstance instance) throws Exception {
        applyFromEsbHeader(instance);
    }

    @Override
    public void afterExecute(Activity activity, ProcessInstance instance) throws Exception {
    }

    @Override
    public void afterComplete(Activity activity, ProcessInstance instance) throws Exception {
    }

    @Override
    public void afterFault(Activity activity, ProcessInstance instance, FaultContext faultContext) throws Exception {
    }

    @Override
    public void onPropertyChange(
            Activity activity,
            ProcessInstance instance,
            String propertyName,
            Object changedValue) throws Exception {
    }

    @Override
    public void onDeploy(ProcessDefinition definition) throws Exception {
    }

    private static void applyFromEsbHeader(ProcessInstance instance) throws Exception {
        return;
    }
}
