package org.uengine.five.overriding;

import org.quartz.JobDataMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.kernel.bpmn.TimerEventJob;

@Component
@Primary
public class FileSimulationTimerEventJob extends TimerEventJob {
    @Override
    @Transactional
    public void fireEvent(JobDataMap jobData) throws Exception {
        Long instanceId;
        try {
            instanceId = Long.valueOf(jobData.getString("instanceId"));
        } catch (NumberFormatException ignored) {
            super.fireEvent(jobData);
            return;
        }

        if (ProcessTransactionContext.isFileSimulationId(instanceId)) {
            ProcessTransactionContext.runFileSimulationCallback(instanceId, () -> super.fireEvent(jobData));
        } else {
            super.fireEvent(jobData);
        }
    }
}
