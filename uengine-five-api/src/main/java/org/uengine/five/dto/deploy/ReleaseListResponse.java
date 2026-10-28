package org.uengine.five.dto.deploy;

import java.util.ArrayList;
import java.util.List;

/** GET /deploy/request · GET /deploy/apply path 목록 (버전 미포함). */
public class ReleaseListResponse {

    private List<String> definitionPaths = new ArrayList<>();

    public List<String> getDefinitionPaths() {
        return definitionPaths;
    }

    public void setDefinitionPaths(List<String> definitionPaths) {
        this.definitionPaths = definitionPaths != null ? definitionPaths : new ArrayList<>();
    }
}
