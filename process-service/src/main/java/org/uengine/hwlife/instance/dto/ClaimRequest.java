package org.uengine.hwlife.instance.dto;

import java.util.List;

import org.uengine.five.dto.RoleMappingCommand;

/**
 * 다중 선점/선점 해제 요청 — POST /instance/multi-claim JSON body.
 *
 * <p>{@code roleMapping.endpoint} 가 비어 있으면 선점 해제(unclaim)로 처리한다.</p>
 */
public class ClaimRequest {

    private List<String> taskIds;
    private RoleMappingCommand roleMapping;

    public List<String> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<String> taskIds) {
        this.taskIds = taskIds;
    }

    public RoleMappingCommand getRoleMapping() {
        return roleMapping;
    }

    public void setRoleMapping(RoleMappingCommand roleMapping) {
        this.roleMapping = roleMapping;
    }
}
