package org.uengine.five.dto;

public class RoleMapping {
    String name;
    String[] resourceNames;
    String[] endpoints;
    String group;
    String groupName;
    String assignGroup;
    String scope;
    Integer assignType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getResourceNames() {
        return resourceNames;
    }

    public void setResourceNames(String[] resourceNames) {
        this.resourceNames = resourceNames;
    }

    public String[] getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(String[] endpoints) {
        this.endpoints = endpoints;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getAssignGroup() {
        return assignGroup;
    }

    public void setAssignGroup(String assignGroup) {
        this.assignGroup = assignGroup;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Integer getAssignType() {
        return assignType;
    }

    public void setAssignType(Integer assignType) {
        this.assignType = assignType;
    }

    public org.uengine.kernel.RoleMapping toKernelRoleMapping() {
        org.uengine.kernel.RoleMapping kernelRoleMapping = org.uengine.kernel.RoleMapping.create();
        kernelRoleMapping.setName(name);

        String resolvedGroup = firstNotEmpty(assignGroup, groupName, group);
        if (resolvedGroup != null) {
            kernelRoleMapping.setAssignGroup(resolvedGroup);
        }
        if (scope != null && !scope.trim().isEmpty()) {
            kernelRoleMapping.setScope(scope.trim());
        }
        if (assignType != null) {
            kernelRoleMapping.setAssignType(assignType);
        }

        if (endpoints != null) {
            for (int i = 0; i < endpoints.length; i++) {
                kernelRoleMapping.setEndpoint(endpoints[i]);
                if (resourceNames != null && resourceNames.length > i) {
                    kernelRoleMapping.setResourceName(resourceNames[i]);
                }
                kernelRoleMapping.moveToAdd();
            }
        }

        return kernelRoleMapping;
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
