package org.uengine.five.service;

// import static org.springframework.hateoas.server.mvc.ControllerLinkBuilder.linkTo;
// import static org.springframework.hateoas.server.mvc.ControllerLinkBuilder.methodOn;

import java.util.List;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;
// import org.springframework.hateoas.server.mvc.ControllerLinkBuilder;
import org.uengine.modeling.resource.IContainer;
import org.uengine.modeling.resource.IResource;
import org.uengine.util.UEngineUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by uengine on 2017. 11. 11..
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefinitionRequest {


    String definition;
    String version;
    /**
     * 표시명. 정의의 사람이 읽는 이름(예: "한화 신용평가 프로세스").
     * 비어 있으면 서버는 표시명 사이드카를 갱신하지 않는다.
     */
    String name;
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
