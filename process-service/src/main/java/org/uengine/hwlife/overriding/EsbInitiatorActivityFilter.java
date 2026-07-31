package org.uengine.hwlife.overriding;

import java.io.Serializable;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.overriding.JPAProcessInstance;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;
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
        if (instance == null) {
            return;
        }
        ProcessInstance local = instance.getLocalInstance();
        if (!(local instanceof JPAProcessInstance)) {
            return;
        }
        JPAProcessInstance jpa = (JPAProcessInstance) local;
        if (!jpa.isNewInstance()) {
            return;
        }
        ProcessInstanceEntity entity = jpa.getProcessInstanceEntity();
        if (entity == null) {
            return;
        }

        EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
        if (header == null) {
            return;
        }

        String emnb = trimToNull(header.getEmnb());
        String belnOrgnCode = trimToNull(header.getBelnOrgnCode());
        if (emnb != null && !hasText(entity.getInitEp())) {
            entity.setInitEp(emnb);
        }
        if (belnOrgnCode != null && !hasText(entity.getInitGroupCd())) {
            entity.setInitGroupCd(belnOrgnCode);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
