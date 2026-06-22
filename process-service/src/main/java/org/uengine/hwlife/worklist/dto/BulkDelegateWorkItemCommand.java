package org.uengine.hwlife.worklist.dto;

import java.util.List;

import org.uengine.five.dto.RoleMappingCommand;

public class BulkDelegateWorkItemCommand {

    private List<String> taskIds;
    private RoleMappingCommand delegatedRoleMapping;
    private Boolean delegateOnlyForWorkitem;

    public List<String> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<String> taskIds) {
        this.taskIds = taskIds;
    }

    public RoleMappingCommand getDelegatedRoleMapping() {
        return delegatedRoleMapping;
    }

    public void setDelegatedRoleMapping(RoleMappingCommand delegatedRoleMapping) {
        this.delegatedRoleMapping = delegatedRoleMapping;
    }

    public Boolean getDelegateOnlyForWorkitem() {
        return delegateOnlyForWorkitem;
    }

    public void setDelegateOnlyForWorkitem(Boolean delegateOnlyForWorkitem) {
        this.delegateOnlyForWorkitem = delegateOnlyForWorkitem;
    }
}
