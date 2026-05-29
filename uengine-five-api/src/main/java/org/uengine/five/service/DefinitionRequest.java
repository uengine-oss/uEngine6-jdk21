package org.uengine.five.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload for /definition/raw/** (putRawDefinition).
 *
 * Kept in uengine-five-api so other services (e.g. process-service) can call
 * definition-service via Feign.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefinitionRequest {

    private String definition;
    private String version;
    /**
     * 표시명. 정의의 사람이 읽는 이름(예: "한화 신용평가 프로세스").
     * 비어 있으면 서버는 표시명 사이드카를 갱신하지 않는다.
     * (사이드바·정의체계도가 파일명 대신 보여주는 값)
     */
    private String name;

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

