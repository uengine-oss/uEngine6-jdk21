package org.uengine.five.dto.deploy;

/** POST /deploy/request 요청 body. */
public class ReleaseRequestCreateRequest {

    private String definitionPath;

    public String getDefinitionPath() {
        return definitionPath;
    }

    public void setDefinitionPath(String definitionPath) {
        this.definitionPath = definitionPath;
    }
}
