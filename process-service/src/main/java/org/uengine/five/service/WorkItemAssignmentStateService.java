package org.uengine.five.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;

@Service
public class WorkItemAssignmentStateService {

    @Autowired
    WorklistRepository worklistRepository;

    @Autowired
    ProcessInstanceRepository processInstanceRepository;

    public AssignmentChangeContext begin(WorklistEntity worklist) {
        if (worklist == null) {
            return null;
        }
        Long rootInstId = worklist.getRootInstId() != null
                ? worklist.getRootInstId()
                : worklist.getInstId();
        return begin(rootInstId);
    }

    public AssignmentChangeContext begin(Long rootInstId) {
        if (rootInstId == null) {
            return null;
        }
        ProcessInstanceEntity instance = processInstanceRepository.findById(rootInstId).orElse(null);
        if (instance == null) {
            return new AssignmentChangeContext(rootInstId, null, null, null);
        }
        return new AssignmentChangeContext(
                rootInstId,
                instance.getCurrEp(),
                instance.getCurrRsNm(),
                instance.getCurrGroupCd());
    }

    public void rememberPrevious(WorklistEntity worklist) {
        if (worklist == null) {
            return;
        }
        worklist.setPrevEndpoint(worklist.getEndpoint());
        worklist.setPrevUserName(worklist.getResName());
        worklist.setPrevGroupCd(worklist.getGroupCd());
    }

    public void rememberPreviousIfChanged(
            WorklistEntity worklist,
            String endpoint,
            String resName,
            String groupCd) {
        if (worklist == null
                || (Objects.equals(worklist.getEndpoint(), endpoint)
                && Objects.equals(worklist.getResName(), resName)
                && Objects.equals(worklist.getGroupCd(), groupCd))) {
            return;
        }
        rememberPrevious(worklist);
    }

    public List<WorklistEntity> reassignActiveLane(
            Long instId,
            String roleName,
            String endpoint,
            String resName,
            String groupCd) {
        if (instId == null || roleName == null || roleName.trim().isEmpty()) {
            return List.of();
        }
        List<WorklistEntity> workitems = worklistRepository.findActiveLaneForUpdate(instId, roleName);
        if (workitems == null || workitems.isEmpty()) {
            return List.of();
        }
        for (WorklistEntity workitem : workitems) {
            if (workitem == null) {
                continue;
            }
            String targetGroupCd = groupCd != null ? groupCd : workitem.getGroupCd();
            rememberPreviousIfChanged(workitem, endpoint, resName, targetGroupCd);
            workitem.setEndpoint(endpoint);
            workitem.setResName(resName);
            workitem.setGroupCd(targetGroupCd);
        }
        return worklistRepository.saveAll(workitems);
    }

    public void finish(AssignmentChangeContext context) {
        if (context == null) {
            return;
        }
        ProcessInstanceEntity instance = processInstanceRepository.findById(context.rootInstId()).orElse(null);
        if (instance == null) {
            return;
        }

        List<WorklistEntity> active = worklistRepository.findActiveByRootOrInstance(context.rootInstId());
        String currEp = joinDistinct(active, WorklistEntity::getEndpoint);
        String currRsNm = joinDistinct(active, WorklistEntity::getResName);
        String currGroupCd = joinDistinct(active, WorklistEntity::getGroupCd);

        if (Objects.equals(instance.getCurrEp(), currEp)
                && Objects.equals(instance.getCurrRsNm(), currRsNm)
                && Objects.equals(instance.getCurrGroupCd(), currGroupCd)) {
            return;
        }

        instance.setPrevCurrEp(context.currEp());
        instance.setPrevCurrRsNm(context.currRsNm());
        instance.setPrevCurrGroupCd(context.currGroupCd());
        instance.setCurrEp(currEp);
        instance.setCurrRsNm(currRsNm);
        instance.setCurrGroupCd(currGroupCd);
        processInstanceRepository.save(instance);
    }

    public void synchronize(Long rootInstId) {
        AssignmentChangeContext context = begin(rootInstId);
        finish(context);
    }

    private static String joinDistinct(List<WorklistEntity> workitems, Function<WorklistEntity, String> getter) {
        Set<String> values = new LinkedHashSet<>();
        if (workitems != null) {
            for (WorklistEntity workitem : workitems) {
                if (workitem == null) {
                    continue;
                }
                String value = getter.apply(workitem);
                if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                    values.add(value.trim());
                }
            }
        }
        return values.isEmpty() ? null : String.join(";", values);
    }

    public record AssignmentChangeContext(
            Long rootInstId,
            String currEp,
            String currRsNm,
            String currGroupCd) {
    }
}
