package org.uengine.five.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
// import org.springframework.hateoas.ResourceSupport;
// import org.springframework.hateoas.Resources;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;
// import org.uengine.five.serializers.BpmnXMLParser;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.NeedArrangementToSerialize;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.UEngineException;
import org.uengine.modeling.resource.ContainerResource;
import org.uengine.modeling.resource.DefaultResource;
import org.uengine.modeling.resource.IContainer;
import org.uengine.modeling.resource.IResource;
import org.uengine.modeling.resource.ResourceManager;
import org.uengine.modeling.resource.Serializer;
import org.uengine.modeling.resource.Version;
import org.uengine.modeling.resource.VersionManager;
//import org.uengine.processpublisher.BPMNUtil;
//import org.uengine.uml.model.ClassDefinition;
import org.uengine.util.UEngineUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by uengine on 2017. 8. 9..
 *
 * Implementation Principles: - REST Maturity Level : 3 (Hateoas)
 * - Not using old uEngine ProcessManagerBean, this replaces the
 * ProcessManagerBean
 * - ResourceManager and CachedResourceManager will be used for definition
 * caching (Not to use the old DefinitionFactory)
 * - json must be Typed JSON to enable object polymorphism
 * - need to change the jackson engine.
 * TODO: accept? typed json is sometimes hard to read
 */
@RestController
public class DefinitionServiceImpl implements DefinitionService, DefinitionXMLService {

    static protected final String RESOURCE_ROOT = "definitions";
    static protected final String ARCHIVE_ROOT = "archive";
    /** 표시명 사이드카 접미. 예: definitions/default/foo.bpmn → definitions/default/foo.bpmn.meta.json */
    static protected final String META_SUFFIX = ".meta.json";

    static final ObjectMapper META_OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    ResourceManager resourceManager;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    InstanceService instanceService;
    // static BpmnXMLParser bpmnXMLParser = new BpmnXMLParser();

    static ObjectMapper objectMapper = createTypedJsonObjectMapper();

    @PostConstruct
    public void init() {
    }

    @RequestMapping(value = DEFINITION, method = RequestMethod.GET, produces = "application/hal+json;charset=UTF-8")
    @Override
    public CollectionModel<DefinitionResource> listDefinition(String basePath) throws Exception {
        return _listDefinition(RESOURCE_ROOT, basePath);
    }

    /**
     * Non-HAL JSON listing for lightweight clients.
     * Used by process-service to enumerate business rule files.
     */
    @Override
    @RequestMapping(value = DEFINITION, method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public String listDefinitionRaw(@RequestParam(value = "basePath", required = false) String basePath)
            throws Exception {
        CollectionModel<DefinitionResource> model = listDefinition(basePath);
        return new ObjectMapper().writeValueAsString(model);
    }

    @RequestMapping(value = "/versions/**", method = RequestMethod.GET)
    public CollectionModel<DefinitionResource> listDefinitionVersions(HttpServletRequest request) throws Exception {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String basePath = "/versions/";
        String defId = fullPath.substring(basePath.length());

        // 디버깅 로그 추가
        System.out.println("fullPath: " + fullPath);
        System.out.println("basePath: " + basePath);
        System.out.println("defId: " + defId);
        // relativePath를 사용하여 버전을 가져오는 로직
        // String versions = getVersionsForPath(relativePath);
        return _listDefinitionVersions("archive/", defId);
    }

    private CollectionModel<DefinitionResource> _listDefinitionVersions(String resourceRoot, String basePath)
            throws Exception {

        if (basePath == null) {
            basePath = "";
        }

        IContainer resource = new ContainerResource();
        resource.setPath(resourceRoot + basePath);
        List<IResource> resources = resourceManager.listFiles(resource);

        List<DefinitionResource> definitions = new ArrayList<DefinitionResource>();
        for (IResource resource1 : resources) {
            DefinitionResource definition = new DefinitionResource(resource1);
            definition.setVersion(definition.name.replace(".bpmn", ""));
            definitions.add(definition);
        }
        definitions.sort(Comparator.comparing(def -> {
            String version = def.getVersion();
            try {
                return Float.parseFloat(version);
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }));

        return CollectionModel.of(definitions);
    }

    private CollectionModel<DefinitionResource> _listDefinition(String resourceRoot, String basePath) throws Exception {

        if (basePath == null) {
            basePath = "";
        }

        IContainer resource = new ContainerResource();
        resource.setPath(resourceRoot + "/" + basePath);
        List<IResource> resources = resourceManager.listFiles(resource);

        List<DefinitionResource> definitions = new ArrayList<DefinitionResource>();
        for (IResource resource1 : resources) {
            // 표시명 사이드카(.meta.json)·map.json·metrics.json 등 메타데이터는 트리에 노출하지 않는다.
            if (shouldHideFromDefinitionTree(resource1)) {
                continue;
            }
            DefinitionResource definition = new DefinitionResource(resource1);
            // 표시명 사이드카가 있으면 definitionName 으로 채워 클라이언트가 파일명 대신 보여줄 수 있게.
            enrichDefinitionNameFromSidecar(definition, resource1);
            definitions.add(definition);
            definition.path = definition.path.replace("definitions/", "");
        }

        return CollectionModel.of(definitions);
    }

    /**
     * 사이드바·정의체계도 트리에 표시하지 않을 파일 판별.
     * - {@code *.meta.json} : 표시명 사이드카
     * - 루트의 {@code map.json}/{@code metrics.json} 등 .json 일반 메타
     */
    private boolean shouldHideFromDefinitionTree(IResource resource) {
        if (resource instanceof IContainer) {
            return false;
        }
        String name = resource.getName();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(META_SUFFIX) || lower.endsWith(".json");
    }

    /**
     * BPMN/form/rule 등 정의 파일의 사이드카에서 표시명을 읽어 {@link DefinitionResource#setDefinitionName}.
     * 사이드카가 없거나 파싱 실패해도 목록 조회 자체는 실패하지 않는다.
     */
    private void enrichDefinitionNameFromSidecar(DefinitionResource definition, IResource resource1) {
        String displayName = readDisplayNameFromSidecar(resource1.getPath());
        if (displayName != null && !displayName.isBlank()) {
            definition.setDefinitionName(displayName);
        }
    }

    /**
     * 주어진 정의 파일 경로(예: {@code definitions/default/foo.bpmn})에 대응하는
     * {@code definitions/default/foo.bpmn.meta.json} 사이드카에서 {@code name} 필드를 읽어 반환.
     * 파일이 없거나 JSON 파싱 실패 시 {@code null}.
     */
    private String readDisplayNameFromSidecar(String definitionResourcePath) {
        if (definitionResourcePath == null || definitionResourcePath.isBlank()) {
            return null;
        }
        String metaPath = definitionResourcePath + META_SUFFIX;
        try {
            IResource meta = new DefaultResource(metaPath);
            if (!resourceManager.exists(meta)) {
                return null;
            }
            String body;
            try (InputStream in = resourceManager.getInputStream(meta)) {
                if (in == null) {
                    return null;
                }
                body = new String(IOUtils.toByteArray(in), StandardCharsets.UTF_8);
            }
            if (body == null || body.isBlank()) {
                return null;
            }
            java.util.Map<?, ?> map = META_OBJECT_MAPPER.readValue(body, java.util.Map.class);
            Object n = map.get("name");
            return n == null ? null : String.valueOf(n).trim();
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 정의 저장 시 {@link DefinitionRequest#getName()} 가 있으면 사이드카({@code .meta.json})에 표시명 기록.
     * - 사이드카 경로: {@code definitions/{path}.meta.json}
     * - 입력이 비어 있거나 공백뿐이면 기록하지 않는다(기존 사이드카는 그대로 유지).
     * - 255자 초과 시 자른다(클라이언트와 일관).
     */
    private void writeDisplayNameSidecar(String definitionResourcePath, String displayName) {
        if (definitionResourcePath == null || definitionResourcePath.isBlank()) {
            return;
        }
        if (displayName == null) {
            return;
        }
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.length() > 255) {
            trimmed = trimmed.substring(0, 255);
        }
        try {
            java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("name", trimmed);
            String json = META_OBJECT_MAPPER.writeValueAsString(payload);
            DefaultResource meta = new DefaultResource(definitionResourcePath + META_SUFFIX);
            resourceManager.save(meta, json);
        } catch (Exception ignore) {
            // 사이드카 실패가 본체 저장을 실패시키지 않도록 swallow.
        }
    }

    @RequestMapping(value = "/version/{version}" + DEFINITION + "/", method = RequestMethod.GET)
    public CollectionModel<DefinitionResource> listVersionDefinitions(@PathVariable("version") String version,
            String basePath) throws Exception {
        VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);

        return _listDefinition(versionManager.versionDirectoryOf(new Version(version)), basePath);
    }

    @RequestMapping(value = "/version", method = RequestMethod.GET)
    public CollectionModel<VersionResource> listVersions() throws Exception {
        VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);

        List<VersionResource> versionResources = new ArrayList<VersionResource>();
        for (Version version : versionManager.listVersions()) {
            VersionResource versionResource = new VersionResource(version);
            versionResources.add(versionResource);
        }

        return CollectionModel.of(versionResources);
    }

    @RequestMapping(value = "/version", method = RequestMethod.POST)
    public CollectionModel<VersionResource> versionUp(Version version, @RequestParam(value = "major", defaultValue = "false") boolean major,
            @RequestParam(value = "makeProduction", defaultValue = "false") boolean makeProduction) throws Exception {

        VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);
        versionManager.load("codi", null);

        if (major)
            versionManager.majorVersionUp();
        else
            versionManager.minorVersionUp();

        return listVersions();

    }

    @RequestMapping(value = "/version/{version:.+}/production", method = RequestMethod.POST)
    public VersionResource makeProduction(@PathVariable("version") String version) throws Exception {

        VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);
        versionManager.load("codi", null);

        Version versionObj = new Version(version);
        versionManager.makeProductionVersion(versionObj);

        // VersionResource versionResource = new VersionResource(versionObj);

        return getVersion(version);
    }

    @RequestMapping(value = "/version/production", method = RequestMethod.GET)
    public VersionResource getProduction() throws Exception {

        VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);
        versionManager.load("codi", null);

        Version versionObj = versionManager.getProductionVersion();

        return new VersionResource(versionObj);
    }

    @RequestMapping(value = "/version/{version:.+}", method = RequestMethod.GET)
    public VersionResource getVersion(@PathVariable("version") String version) throws Exception {

        VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);
        List<Version> versions = versionManager.listVersions();

        for (Version theVersion : versions) {
            if (theVersion.equals(new Version(version))) {
                VersionResource versionResource = new VersionResource(theVersion);

                return versionResource;
            }
        }

        throw new ResourceNotFoundException(); // make 404 error
    }

    @RequestMapping(value = DEFINITION
            + "/{defPath:.+}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    @Override
    @SuppressWarnings("rawtypes")
    public RepresentationModel getDefinition(@PathVariable("defPath") String definitionPath) throws Exception {

        // case of directory:
        IResource resource = new DefaultResource(RESOURCE_ROOT + "/" + definitionPath);
        if (resourceManager.exists(resource) && resourceManager.isContainer(resource)) { // is a folder
            return listDefinition(definitionPath);
        }

        // case of file:
        // definitionPath = UEngineUtil.getNamedExtFile(definitionPath, "xml");

        resource = new DefaultResource(RESOURCE_ROOT + "/" + definitionPath);

        if (!resourceManager.exists(resource)) {
            throw new ResourceNotFoundException(); // make 404 error
        }

        DefinitionResource halDefinition = new DefinitionResource(resource);
        // 단건 조회도 사이드카 표시명을 반영해야 클라이언트가 헤더/타이틀에 일관되게 노출 가능.
        enrichDefinitionNameFromSidecar(halDefinition, resource);

        return halDefinition;

    }

    @RequestMapping(value = DEFINITION + "/**", method = RequestMethod.GET)
    public Object getDefinition(HttpServletRequest request) throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String definitionPath = path.substring(DEFINITION.length() + 1);

        return getDefinition(definitionPath);

    }

    /**
     * TODO: need ACL referenced by token
     * 
     * @throws Exception
     */
    @RequestMapping(value = DEFINITION + "/**", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    public DefinitionResource renameOrMove(@RequestBody DefinitionResource definition_, HttpServletRequest request)
            throws Exception {

        DefinitionResource definition = definition_;

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String definitionPath = path.substring(DEFINITION.length());
        if (definitionPath.indexOf(".") != -1) {
            definitionPath = UEngineUtil.getNamedExtFile(definitionPath, "xml");
        }
        IResource resource = new DefaultResource(RESOURCE_ROOT + "/" + definitionPath);

        if (!definition.getPath().equals(definitionPath)) {
            String newPath = RESOURCE_ROOT + "/" + definition.getPath();
            resourceManager.rename(resource, newPath);
            // 본체 rename 후 사이드카도 같이 이동. 실패해도 본체는 이미 이동했으니 swallow.
            try {
                IResource oldMeta = new DefaultResource(resource.getPath() + META_SUFFIX);
                if (resourceManager.exists(oldMeta)) {
                    resourceManager.rename(oldMeta, newPath + META_SUFFIX);
                }
            } catch (Exception ignore) {
            }
            return new DefinitionResource(new ContainerResource(newPath));
        }

        return new DefinitionResource(resource);
    }

    @RequestMapping(value = DEFINITION + "/**", method = { RequestMethod.POST })
    public DefinitionResource createFolder(@RequestBody DefinitionResource newResource_, HttpServletRequest request)
            throws Exception {

        DefinitionResource newResource = newResource_;

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String definitionPath = path.substring(DEFINITION.length());

        if (newResource == null) {
            if (definitionPath.indexOf(".") == -1) { // it is a package (directory)
                IContainer container = new ContainerResource();
                container.setPath(RESOURCE_ROOT + "/" + definitionPath);
                resourceManager.createFolder(container);
                return new DefinitionResource(container);
            } else {
                throw new Exception(
                        "Only folder can be created with this method. Use POST : " + DEFINITION_RAW + " instead.");
            }
        } else {
            String example = "e.g.{\"name\": \"folder\", \"directory\":true}";

            Assert.notNull(newResource.getName(), "folder name must be present. " + example);
            Assert.isTrue(newResource.isDirectory(), "On directory can be created with this method. " + example);

            IContainer container = new ContainerResource();
            container.setPath(RESOURCE_ROOT + definitionPath + "/" + newResource.getName());
            resourceManager.createFolder(container);

            return new DefinitionResource(container);
        }

    }

    @RequestMapping(value = DEFINITION + "/**", method = { RequestMethod.DELETE })
    public void deleteDefinition(HttpServletRequest request) throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String definitionPath = path.substring(DEFINITION.length());
        if (path.indexOf(".") == -1) {
            definitionPath = UEngineUtil.getNamedExtFile(definitionPath, "bpmn");
        }

        IResource resource = new DefaultResource(RESOURCE_ROOT + definitionPath);
        resourceManager.delete(resource);

        // 본체 삭제와 함께 표시명 사이드카도 정리 — 잔여 메타 파일이 다음 listDefinition 에서 떠다니지 않게.
        try {
            IResource meta = new DefaultResource(resource.getPath() + META_SUFFIX);
            if (resourceManager.exists(meta)) {
                resourceManager.delete(meta);
            }
        } catch (Exception ignore) {
            // 본체 삭제는 이미 성공했으므로 사이드카 삭제 실패는 swallow.
        }

    }

    // ----------------- raw definition services -------------------- //

    @SuppressWarnings("deprecation")
    public static ObjectMapper createTypedJsonObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setVisibilityChecker(objectMapper.getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // ignore null
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT); // ignore zero and false when it is int
                                                                                 // or boolean
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        objectMapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "_type");
        return objectMapper;

    }

    /**
     * TODO: need ACL referenced by token
     * 
     * @param definition
     * @throws Exception
     */
    // @Override
    // @RequestMapping(value = DEFINITION_RAW + "/{defPath:.+}", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    // public DefinitionResource putRawDefinition(@PathVariable("defPath") String definitionPath,
    //         @RequestBody DefinitionRequest definitionRequest) throws Exception {

    //     String dp = definitionPath;
    //     if (!dp.startsWith("/")) {
    //         dp = "/" + dp;
    //     }

    //     // directory
    //     if (dp.indexOf(".") == -1) {
    //         IContainer container = new ContainerResource();
    //         container.setPath(RESOURCE_ROOT + dp);
    //         resourceManager.createFolder(container);
    //         return new DefinitionResource(container);
    //     }

    //     String fileExt = UEngineUtil.getFileExt(dp);

    //     // archive only for bpmn versions
    //     if (definitionRequest != null && definitionRequest.getVersion() != null && "bpmn".equalsIgnoreCase(fileExt)) {
    //         DefaultResource versionResource = new DefaultResource(
    //                 "/archive" + dp + "/" + definitionRequest.getVersion() + ".bpmn");
    //         resourceManager.save(versionResource, definitionRequest.getDefinition());
    //     }

    //     DefaultResource resource = new DefaultResource(RESOURCE_ROOT + dp);
    //     resourceManager.save(resource, definitionRequest.getDefinition());
    //     instanceService.postCreatedRawDefinition(dp);

    //     return new DefinitionResource(resource);
    // }

    /**
     * Feign {@link DefinitionService#putRawDefinition} 및 {@link #putRawDefinitionByParam} 가 호출한다.
     * (기존 스텁이 자기 자신을 호출해 StackOverflow 가 났음)
     */
    @Override
    public Object putRawDefinition(String definitionPath, DefinitionRequest definitionRequest) throws Exception {
        return saveRawDefinitionCore(definitionPath, definitionRequest);
    }

    /**
     * {@code PUT/POST /definition/raw/**} — 경로 접미사로 defPath 전달 (쿼리 {@code defPath} 와 별개).
     * 메서드명을 {@code putRawDefinition} 과 분리해 Feign/스프링 매핑과의 혼동·재귀 위험을 줄인다.
     */
    @RequestMapping(value = DEFINITION_RAW + "/**", method = { RequestMethod.POST, RequestMethod.PUT })
    public DefinitionResource putRawDefinitionWithPathSuffix(@RequestBody DefinitionRequest definitionRequest,
            HttpServletRequest request)
            throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String definitionPath = path.substring(DEFINITION_RAW.length());

        return saveRawDefinitionCore(definitionPath, definitionRequest);
    }

    /**
     * raw 정의 저장 공통 처리 (경로 정규화, 아카이브, process-service 알림).
     */
    private DefinitionResource saveRawDefinitionCore(String definitionPath, DefinitionRequest definitionRequest)
            throws Exception {
        if (definitionRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definitionRequest is required");
        }

        String dp = definitionPath == null ? "" : definitionPath.trim().replace("\\", "/");
        if (dp.startsWith("/")) {
            dp = dp.substring(1);
        }

        String fileName = dp.contains("/") ? dp.substring(dp.lastIndexOf("/") + 1) : dp;
        if (!fileName.contains(".")) {
            dp = dp + ".bpmn";
        }

        String fileExt = UEngineUtil.getFileExt(dp);

        // 버전 아카이브는 BPMN에 대해서만 저장
        if (definitionRequest.getVersion() != null && "bpmn".equalsIgnoreCase(fileExt)) {
            DefaultResource versionResource = new DefaultResource(
                    "/archive/" + dp + "/" + definitionRequest.getVersion() + ".bpmn");
            resourceManager.save(versionResource, definitionRequest.getDefinition());
        }

        DefaultResource resource = new DefaultResource(RESOURCE_ROOT + "/" + dp);
        resourceManager.save(resource, definitionRequest.getDefinition());

        // 표시명 사이드카 저장 — 본체 저장 직후 동기 기록(사이드바·체계도 다음 조회에 즉시 반영).
        writeDisplayNameSidecar(resource.getPath(), definitionRequest.getName());

        // BPMN만 process-service 배포 파이프라인 호출 (중복 호출 제거)
        if ("bpmn".equalsIgnoreCase(fileExt)) {
            try {
                instanceService.postCreatedRawDefinition(dp);
            } catch (FeignException fe) {
                HttpStatus status = HttpStatus.resolve(fe.status());
                if (status == null) {
                    status = HttpStatus.BAD_GATEWAY;
                }

                String body = fe.contentUTF8();
                String summarized = summarizeFeignBody(body, fe);
                throw new ResponseStatusException(status,
                        "[process-service:/definition-changes 실패] definitionPath=" + dp + "\n" + summarized,
                        fe);
            }
        }

        if (dp.indexOf(".") == -1) { // it is a package (directory)
            IContainer container = new ContainerResource();
            container.setPath(RESOURCE_ROOT + "/" + dp);
            resourceManager.createFolder(container);
            return new DefinitionResource(container);
        }

        return new DefinitionResource(resource);
    }

    private String summarizeFeignBody(String body, FeignException fe) {
        if (body == null || body.isBlank()) {
            String fallback = "(empty body) status=" + fe.status();
            if (fe.getMessage() != null && !fe.getMessage().isBlank()) {
                fallback += " message=" + fe.getMessage();
            }
            return fallback;
        }
        // Spring Boot 기본 에러 JSON이면 message(또는 trace)만 뽑아낸다.
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<?, ?> map = om.readValue(body, java.util.Map.class);
            Object msg = map.get("message");
            Object trace = map.get("trace");

            // 우선순위:
            // 1) message 안에 "Stacktrace:"가 있으면 그 뒤만 리턴
            // 2) trace 필드가 있으면 trace만 리턴
            // 3) message만 리턴
            if (msg != null) {
                String m = String.valueOf(msg);
                int idx = m.indexOf("Stacktrace:");
                if (idx >= 0) {
                    String only = m.substring(idx + "Stacktrace:".length());
                    // leading newline 제거
                    if (only.startsWith("\n")) only = only.substring(1);
                    return only;
                }
            }
            if (trace != null) {
                return String.valueOf(trace);
            }
            if (msg != null) {
                return String.valueOf(msg);
            }
        } catch (Exception ignore) {
        }
        // JSON이 아니면 길이 제한만 적용
        int limit = 4000;
        if (body.length() > limit) {
            return body.substring(0, limit) + "\n...(truncated " + (body.length() - limit) + " chars)";
        }
        return body;
    }

    @RequestMapping(value = DEFINITION_RAW
            + "/{defPath:.+}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public Object getRawDefinition(@PathVariable("defPath") String definitionPath/*
                                                                                  * , @RequestParam(value = "unwrap",
                                                                                  * required = false) boolean unwrap
                                                                                  */) throws Exception {
        String version = null;
        if (definitionPath.contains("/version/")) {
            String[] parts = definitionPath.split("/version/");
            definitionPath = parts[0];
            version = parts[1];
        }
        if (definitionPath.indexOf(".") == -1) {
            definitionPath = UEngineUtil.getNamedExtFile(RESOURCE_ROOT + "/" + definitionPath, "xml");
        }
        if (!(definitionPath.startsWith(RESOURCE_ROOT))) {
            definitionPath = RESOURCE_ROOT + "/" + definitionPath;
        }
        // 무조건 xml 파일로 결국 저장됨.
        DefaultResource resource = new DefaultResource(definitionPath);
        Serializable definition = (Serializable) getDefinitionLocal(resource.getPath(), version);

        // if(unwrap) {
        // return objectMapper.writeValueAsString(definition);
        // }else{
        // DefinitionWrapper definitionWrapper = new DefinitionWrapper(definition);
        // String uEngineProcessJSON =
        // objectMapper.writeValueAsString(definitionWrapper);
        return definition;
        // }

    }

    /**
     * Feign-friendly raw definition getter (full path via query param).
     */
    @RequestMapping(value = DEFINITION_RAW, method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public Object getRawDefinitionByParam(@RequestParam("defPath") String definitionPath) throws Exception {
        return getRawDefinition(definitionPath);
    }

    @RequestMapping(value = DEFINITION_RAW
            + "/**", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public Object getRawDefinition(HttpServletRequest request/* , @RequestParam("unwrap") boolean unwrap */)
            throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String definitionPath = path.substring(DEFINITION_RAW.length() + 1);

        return getRawDefinition(definitionPath);

    }

    /**
     * Feign-friendly raw definition saver (full path via query param).
     */
    @RequestMapping(value = DEFINITION_RAW, method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    public DefinitionResource putRawDefinitionByParam(@RequestParam("defPath") String definitionPath,
            @RequestBody DefinitionRequest definitionRequest) throws Exception {
        // putRawDefinition(String, …) 경유 시 동일 시그니처 재귀/프록시 혼선 방지 — 코어로 직접 위임
        return saveRawDefinitionCore(definitionPath, definitionRequest);
    }

    @RequestMapping(value = DEFINITION_SYSTEM + "/**", method = { RequestMethod.POST, RequestMethod.PUT })
    public DefinitionResource putRawSystem(@RequestBody String definition, HttpServletRequest request)
            throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String name = path.substring(DEFINITION_SYSTEM.length());
        // if (definitionPath.indexOf(".") == -1) { // it is a package (directory)
        // IContainer container = new ContainerResource();
        // container.setPath(RESOURCE_ROOT + "/" + definitionPath);
        // resourceManager.createFolder(container);
        // return new DefinitionResource(container);
        // }
        String definitionPath = RESOURCE_ROOT + "/system" + name + ".json";
        String fileExt = UEngineUtil.getFileExt(definitionPath);

        // 무조건 xml 파일로 결국 저장됨.
        DefaultResource resource = new DefaultResource(definitionPath);

        if (fileExt.endsWith("json")) {
            resourceManager.save(resource, definition);
        } else {
            throw new Exception("unknown resource type: " + definitionPath);
        }

        return new DefinitionResource(resource);
    }

    @RequestMapping(value = DEFINITION_MAP + "/**", method = { RequestMethod.POST, RequestMethod.PUT })
    public DefinitionResource putRawDefinitionMap(@RequestBody String definition, HttpServletRequest request)
            throws Exception {

        // String path = (String)
        // request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        // if (definitionPath.indexOf(".") == -1) { // it is a package (directory)
        // IContainer container = new ContainerResource();
        // container.setPath(RESOURCE_ROOT + "/" + definitionPath);
        // resourceManager.createFolder(container);
        // return new DefinitionResource(container);
        // }
        String definitionPath = RESOURCE_ROOT + "/" + "map.json";
        String fileExt = UEngineUtil.getFileExt(definitionPath);

        // 무조건 xml 파일로 결국 저장됨.
        DefaultResource resource = new DefaultResource(definitionPath);

        if (fileExt.endsWith("json")) {
            resourceManager.save(resource, definition);
        } else {
            throw new Exception("unknown resource type: " + definitionPath);
        }

        return new DefinitionResource(resource);
    }

    @RequestMapping(value = DEFINITION_SYSTEM, method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public RepresentationModel<?> getSystem() throws Exception {

        // case of directory:
        IResource resource = new DefaultResource(RESOURCE_ROOT + "/");
        if (resourceManager.exists(resource) && resourceManager.isContainer(resource)) { // is a folder
            return _listSystem(RESOURCE_ROOT, "system");
        }

        return null;
    }

    private CollectionModel<DefinitionResource> _listSystem(String resourceRoot, String basePath) throws Exception {

        if (basePath == null) {
            basePath = "";
        }

        IContainer resource = new ContainerResource();
        resource.setPath(resourceRoot + "/" + basePath);
        List<IResource> resources = resourceManager.listFiles(resource);

        List<DefinitionResource> definitions = new ArrayList<DefinitionResource>();
        for (IResource resource1 : resources) {
            DefinitionResource definition = new DefinitionResource(resource1);
            definitions.add(definition);
        }

        return CollectionModel.of(definitions);
    }

    @RequestMapping(value = DEFINITION
            + "/release/{releaseVerison}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> releaseVersions(@PathVariable("releaseVerison") String releaseVerison) throws Exception {

        // // case of directory:
        // IResource resource = new DefaultResource(RESOURCE_ROOT + "/");
        // if (resourceManager.exists(resource) &&
        // resourceManager.isContainer(resource)) { // is a folder
        // return _listSystem(RESOURCE_ROOT, "system");
        // }

        File resourceDir = new File(RESOURCE_ROOT + "/");
        if (resourceDir.exists() && resourceDir.isDirectory()) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);

            zipDirectory(resourceDir, resourceDir.getName(), zipOutputStream);

            zipOutputStream.close();
            byteArrayOutputStream.close();

            byte[] zipBytes = byteArrayOutputStream.toByteArray();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + releaseVerison + ".zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Resource not found or is not a directory");

    }

    private void zipDirectory(File folder, String parentFolder, ZipOutputStream zipOutputStream) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zipOutputStream);
                continue;
            }
            zipOutputStream.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
            FileInputStream fileInputStream = new FileInputStream(file);
            IOUtils.copy(fileInputStream, zipOutputStream);
            fileInputStream.close();
            zipOutputStream.closeEntry();
        }
    }

    public void unzipDirectory(InputStream inputStream, String releaseName) throws IOException {
        byte[] buffer = new byte[1024];

        try (ZipInputStream zis = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                String filePath = fileName;
                File newFile = new File(filePath);

                System.out.println("Processing file: " + newFile.getAbsolutePath());

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory: " + parent);
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                addArchive(fileName, releaseName, filePath);

                zipEntry = zis.getNextEntry();
            }

            zis.closeEntry();
        } catch (IllegalArgumentException e) {
            System.err.println("Error processing ZIP entry: " + e.getMessage());
            throw new IOException("Error processing ZIP entry", e);
        }
    }

    private void addArchive(String fileName, String releaseName, String filePath) throws IOException {
        if (fileName.lastIndexOf('.') == -1)
            return;
        String extension = fileName.substring(fileName.lastIndexOf('.'));
        String uploadPath = ARCHIVE_ROOT + File.separator;
        if (".bpmn".equals(extension) || ".form".equals(extension)) {
            String folderName = fileName.replace(RESOURCE_ROOT + "/", "");
            File folder = new File(uploadPath, folderName);

            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("폴더 생성 실패: " + folder.getAbsolutePath());
            }
            File sourceFile = new File(filePath);
            File targetReleaseFile = new File(uploadPath + folderName, releaseName + extension);
            byte[] buffer = new byte[1024];
            try (FileInputStream fis = new FileInputStream(sourceFile);
                    FileOutputStream fos = new FileOutputStream(targetReleaseFile)) {
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }
        }
    }

    @RequestMapping(value = "/definition/upload", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadDefinition(@RequestParam("file") MultipartFile file) {
        try {
            String uploadPath = RESOURCE_ROOT + File.separator;
            File targetFile = new File(uploadPath);

            if (!targetFile.exists()) {
                targetFile.mkdirs();
            }

            if (targetFile.exists()) {
                String releaseNameWithoutExtension = file.getOriginalFilename().substring(0,
                        file.getOriginalFilename().lastIndexOf('.'));
                unzipDirectory(file.getInputStream(), releaseNameWithoutExtension);
            }

            return ResponseEntity.ok("파일이 성공적으로 업로드되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @RequestMapping(value = DEFINITION_SYSTEM
            + "/**", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public Object getRawSystem(HttpServletRequest request) throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String name = path.substring(DEFINITION_SYSTEM.length());
        String definitionPath = RESOURCE_ROOT + "/system" + name + ".json";
        // 무조건 xml 파일로 결국 저장됨.
        DefaultResource resource = new DefaultResource(definitionPath);
        Serializable definition = (Serializable) getDefinitionLocal(resource.getPath(), null);

        // if(unwrap) {
        // return objectMapper.writeValueAsString(definition);
        // }else{
        // DefinitionWrapper definitionWrapper = new DefinitionWrapper(definition);
        // String uEngineProcessJSON =
        // objectMapper.writeValueAsString(definitionWrapper);
        return definition;
        // }

    }

    @RequestMapping(value = DEFINITION_MAP, method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public Object getRawDefinitionMap() throws Exception {
        String definitionPath = RESOURCE_ROOT + "/" + "map.json";
        // 무조건 xml 파일로 결국 저장됨.
        DefaultResource resource = new DefaultResource(definitionPath);
        Serializable definition = (Serializable) getDefinitionLocal(resource.getPath(), null);

        // if(unwrap) {
        // return objectMapper.writeValueAsString(definition);
        // }else{
        // DefinitionWrapper definitionWrapper = new DefinitionWrapper(definition);
        // String uEngineProcessJSON =
        // objectMapper.writeValueAsString(definitionWrapper);
        return definition;
        // }

    }

    @RequestMapping(value = DEFINITION
            + "/xml/{defPath:.+}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public String getXMLDefinition(@PathVariable("defPath") String definitionPath,
            @RequestParam("version") String version) throws Exception {

        definitionPath = definitionPath.startsWith(RESOURCE_ROOT) ? definitionPath.replace(RESOURCE_ROOT, "")
                : definitionPath;
        definitionPath = UEngineUtil.getNamedExtFile(definitionPath, "xml");

        // replace to production version if requested:
        if (version != null) {
            VersionManager versionManager = GlobalContext.getComponent(VersionManager.class);
            versionManager.load("codi", null);

            definitionPath = versionManager.getProductionResourcePath(definitionPath);
        }

        Serializable definition = (Serializable) getDefinitionLocal(definitionPath, null);
        String uEngineProcessXML = Serializer.serialize(definition);
        return uEngineProcessXML;

    }

    @RequestMapping(value = DEFINITION
            + "/xml/**", method = RequestMethod.GET, produces = "application/xml;charset=UTF-8")
    public String getXMLDefinition(HttpServletRequest request) throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String definitionPath = path.substring((DEFINITION + "/xml").length() + 1);

        String version = request.getParameter("version");

        return getXMLDefinition(definitionPath, version);

    }

    public Object getDefinitionLocal(String definitionPath, String version) throws Exception {

        try {
            if (definitionPath.indexOf(".") == -1) {
                definitionPath = definitionPath + ".xml";
            }
            if (version != null) {
                definitionPath = definitionPath.replace("definitions/", "archive/");
                definitionPath = definitionPath + "/" + version + ".bpmn";
            }
            IResource resource = new DefaultResource(
                    definitionPath);
            Object definition = resourceManager.getObject(resource);

            // TODO: move to framework
            if (definition instanceof NeedArrangementToSerialize) {
                ((NeedArrangementToSerialize) definition).afterDeserialization();
            }

            if (definition instanceof ProcessDefinition) {
                ProcessDefinition processDefinition = (ProcessDefinition) definition;
                { // TODO: will be moved to afterDeserialize of ProcessDefinition
                    processDefinition.setId(resource.getPath().substring(RESOURCE_ROOT.length() + 1));
                    if (processDefinition.getName() == null) {
                        processDefinition.setName(resource.getPath());
                    }
                }
            }

            return definition;

        } catch (Exception e) {
            throw new UEngineException("Error when to load definition: " + definitionPath, e);
        }

    }

}
