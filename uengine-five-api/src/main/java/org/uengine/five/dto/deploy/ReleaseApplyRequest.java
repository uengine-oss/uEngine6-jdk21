package org.uengine.five.dto.deploy;

import java.util.List;

/** POST /deploy/apply 요청 body. */
public class ReleaseApplyRequest {

    private List<String> definitionPaths;

    public List<String> getDefinitionPaths() {
        return definitionPaths;
    }

    public void setDefinitionPaths(List<String> definitionPaths) {
        this.definitionPaths = definitionPaths;
    }
}
