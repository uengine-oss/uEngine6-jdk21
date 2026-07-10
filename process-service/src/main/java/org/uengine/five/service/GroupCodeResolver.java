package org.uengine.five.service;

import java.util.List;

import org.uengine.kernel.RoleMapping;
import org.uengine.util.UEngineUtil;

public final class GroupCodeResolver {

    private GroupCodeResolver() {
    }

    public static String resolveFromUserGroups(List<String> groups, String laneAssignGroup) {
        String group = firstGroup(groups);
        return UEngineUtil.isNotEmpty(group) ? group : normalize(laneAssignGroup);
    }

    public static String resolveFromRoleMapping(RoleMapping roleMapping, String laneAssignGroup) {
        if (roleMapping == null) {
            return normalize(laneAssignGroup);
        }

        String endpointGroup = resolveFromEndpoint(roleMapping.getEndpoint(), null);
        if (UEngineUtil.isNotEmpty(endpointGroup)) {
            return endpointGroup;
        }

        String roleGroup = firstNotEmpty(roleMapping.getGroupId(), roleMapping.getGroupName(), roleMapping.getAssignGroup());
        return UEngineUtil.isNotEmpty(roleGroup) ? roleGroup : normalize(laneAssignGroup);
    }

    public static String resolveFromEndpoint(String endpoint, String laneAssignGroup) {
        if (UEngineUtil.isNotEmpty(endpoint)) {
            try {
                IAMService iamService = IAMServiceFactory.getDefault();
                if (iamService != null) {
                    String group = firstGroup(iamService.getUserGroups(endpoint.trim()));
                    if (UEngineUtil.isNotEmpty(group)) {
                        return group;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return normalize(laneAssignGroup);
    }

    public static String firstGroup(List<String> groups) {
        if (groups == null) {
            return null;
        }
        for (String group : groups) {
            String normalized = normalize(group);
            if (UEngineUtil.isNotEmpty(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static String firstNotEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (UEngineUtil.isNotEmpty(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (!UEngineUtil.isNotEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}
