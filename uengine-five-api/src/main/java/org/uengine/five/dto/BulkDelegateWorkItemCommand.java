package org.uengine.five.dto;

import java.util.List;

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
