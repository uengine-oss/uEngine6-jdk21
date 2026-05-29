package org.uengine.five.service;

// import static org.springframework.hateoas.server.mvc.ControllerLinkBuilder.linkTo;
// import static org.springframework.hateoas.server.mvc.ControllerLinkBuilder.methodOn;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;
// import org.springframework.hateoas.server.mvc.ControllerLinkBuilder;
import org.uengine.modeling.resource.IContainer;
import org.uengine.modeling.resource.IResource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by uengine on 2017. 11. 11..
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Relation(value = "definition", collectionRelation = "definitions")
public class DefinitionResource extends RepresentationModel {

    String name;

    public DefinitionResource() {
    }

    public DefinitionResource(IResource resource1) throws Exception {
        setName(resource1.getName());
        setDirectory(resource1 instanceof IContainer);
        setPath(resource1.getPath());

        add(
                linkTo(
                        methodOn(DefinitionServiceImpl.class)
                                .getDefinition(
                                        resource1.getPath()))
                        .withSelfRel());

        if (!isDirectory()) {
            add(
                    linkTo(
                            methodOn(DefinitionServiceImpl.class)
                                    .getRawDefinition(
                                            resource1.getPath()))
                            .withRel("raw"));
            // add(
            // linkTo(
            // methodOn(DefinitionServiceImpl.class)
            // .getXMLDefinition(
            // UEngineUtil.getNamedExtFile(relativePath, "json"), false
            // )
            // ).withRel("xml")
            // );
            add(
                    // ControllerLinkBuilder.linkTo(
                    linkTo(
                            methodOn(InstanceService.class)
                                    .start(
                                            null))
                            .withRel("instantiation"));

        }

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    boolean directory;

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String version;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 사용자가 지정한 표시명.
     * {@code name}은 파일명/경로 기반 식별자라 클라이언트의 파일 필터(확장자 매칭)와
     * 라우팅에 그대로 쓰여야 해서, 표시명은 별도 키로 노출한다.
     * (definition-service가 {@code definitions/{path}.meta.json} 사이드카에서 읽어 채움)
     */
    private String definitionName;

    public String getDefinitionName() {
        return definitionName;
    }

    public void setDefinitionName(String definitionName) {
        this.definitionName = definitionName;
    }

}
