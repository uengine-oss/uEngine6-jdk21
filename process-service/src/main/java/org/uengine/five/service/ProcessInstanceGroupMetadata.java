package org.uengine.five.service;

import java.util.List;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.kernel.RoleMapping;

public final class ProcessInstanceGroupMetadata {

    private ProcessInstanceGroupMetadata() {
    }

    public static void applyStartGroup(ProcessInstanceEntity entity, List<String> starterGroups) {
        if (entity == null) {
            return;
        }
        String groupCd = GroupCodeResolver.resolveFromUserGroups(starterGroups, null);
        entity.setInitGroupCd(groupCd);
        entity.setCurrGroupCd(groupCd);
    }

    public static void applyDelegation(ProcessInstanceEntity entity, RoleMapping delegated, String laneAssignGroup) {
        if (entity == null) {
            return;
        }
        entity.setPrevCurrGroupCd(entity.getCurrGroupCd());
        entity.setCurrGroupCd(GroupCodeResolver.resolveFromRoleMapping(delegated, laneAssignGroup));
    }
}
