package org.uengine.five.dto.deploy;

/** POST /deploy/request 응답. */
public class ReleaseRequestCreateResult {

    private String definitionPath;
    private String version;
    private boolean success;
    private String errorMessage;

    public String getDefinitionPath() {
        return definitionPath;
    }

    public void setDefinitionPath(String definitionPath) {
        this.definitionPath = definitionPath;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
