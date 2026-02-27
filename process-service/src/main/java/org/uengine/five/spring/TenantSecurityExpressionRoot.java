package org.uengine.five.spring;

import org.uengine.contexts.UserContext;

public class TenantSecurityExpressionRoot {

    public TenantSecurityExpressionRoot(Object authentication) {
    }

    public Object getPrincipal() {
        return UserContext.getThreadLocalInstance();
    }

}