package org.uengine.hwlife.release;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.deploy.VersionedBpmnFiles;
import org.uengine.five.dto.deploy.ReleaseApplyItemResult;
import org.uengine.five.dto.deploy.ReleaseApplyRequest;
import org.uengine.five.dto.deploy.ReleaseApplyResponse;
import org.uengine.five.dto.deploy.ReleaseListResponse;
import org.uengine.five.dto.deploy.ReleaseRequestCreateRequest;
import org.uengine.five.dto.deploy.ReleaseRequestCreateResult;
import org.uengine.five.service.DefinitionDeployService;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.modeling.resource.DefaultResource;
import org.uengine.modeling.resource.ResourceManager;

/**
 * 공유 {@code uengine.definition.basePath} 에서 배포 요청/적용을 인프로세스로 처리한다.
 * Feign/definition-service 네트워크 호출 없음.
 * 권한 검사는 {@link org.uengine.hwlife.authorization.AuthorizationFacadeService}.
 */
@Service
public class DefinitionDeployLocalImpl implements DefinitionDeployService {

    private static final Logger log = LoggerFactory.getLogger(DefinitionDeployLocalImpl.class);

    private final ResourceManager resourceManager;
    private final InstanceServiceImpl instanceService;
    private final String basePath;

    public DefinitionDeployLocalImpl(
            ResourceManager resourceManager,
            InstanceServiceImpl instanceService,
            @Value("${uengine.definition.basePath}") String basePath) {
        this.resourceManager = resourceManager;
        this.instanceService = instanceService;
        this.basePath = basePath;
    }

    @Override
    public ReleaseListResponse listDeployRequests() throws Exception {
        ReleaseListResponse response = new ReleaseListResponse();
        response.setDefinitionPaths(
                VersionedBpmnFiles.listDefinitionPaths(basePath, VersionedBpmnFiles.ROOT_DEPLOY_REQUEST));
        return response;
    }

    @Override
    public ReleaseRequestCreateResult createDeployRequest(ReleaseRequestCreateRequest request) throws Exception {
        if (request == null || request.getDefinitionPath() == null || request.getDefinitionPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definitionPath is required");
        }

        String path = VersionedBpmnFiles.normalizeDefinitionPath(request.getDefinitionPath());
        ReleaseRequestCreateResult result = new ReleaseRequestCreateResult();
        result.setDefinitionPath(path);

        String version = VersionedBpmnFiles
                .findHighestVersion(basePath, VersionedBpmnFiles.ROOT_ARCHIVE, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No versioned bpmn under archive/" + path));

        try {
            String body = VersionedBpmnFiles.readVersionFile(
                    basePath, VersionedBpmnFiles.ROOT_ARCHIVE, path, version);

            VersionedBpmnFiles.writeVersionFile(
                    basePath, VersionedBpmnFiles.ROOT_DEPLOY_REQUEST, path, version, body);

            result.setVersion(version);
            result.setSuccess(true);
            log.info("Deploy request stored: path={}, version={}", path, version);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Release request failed: path={}", path, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
        }

        return result;
    }

    @Override
    public ReleaseListResponse listDeployApplies() throws Exception {
        ReleaseListResponse response = new ReleaseListResponse();
        response.setDefinitionPaths(VersionedBpmnFiles.listDefinitionPaths(basePath));
        return response;
    }

    @Override
    public ReleaseApplyResponse applyDeploy(ReleaseApplyRequest request) throws Exception {
        if (request == null || request.getDefinitionPaths() == null || request.getDefinitionPaths().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "definitionPaths is required and must not be empty.");
        }

        List<String> targets = new ArrayList<>();
        for (String raw : request.getDefinitionPaths()) {
            if (raw == null || raw.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "definitionPaths contains blank path.");
            }
            targets.add(VersionedBpmnFiles.normalizeDefinitionPath(raw));
        }

        ReleaseApplyResponse response = new ReleaseApplyResponse();
        List<ReleaseApplyItemResult> results = new ArrayList<>();
        int success = 0;

        for (String path : targets) {
            ReleaseApplyItemResult item = applyOne(path);
            results.add(item);
            if (item.isSuccess()) {
                success++;
            }
        }

        response.setResults(results);
        response.setTotalCount(results.size());
        response.setSuccessCount(success);
        response.setFailedCount(results.size() - success);
        return response;
    }

    private ReleaseApplyItemResult applyOne(String definitionPath) {
        ReleaseApplyItemResult result = new ReleaseApplyItemResult();
        String path = VersionedBpmnFiles.normalizeDefinitionPath(definitionPath);
        result.setDefinitionPath(path);

        String archiveVersion = null;
        String previousDefinition = null;
        boolean hadPreviousDefinition = false;
        boolean putRawSucceeded = false;

        try {
            String applyVersion = VersionedBpmnFiles
                    .findHighestVersion(basePath, VersionedBpmnFiles.ROOT_DEPLOY_APPLY, path)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No versioned bpmn under deploy-apply/" + path));

            hadPreviousDefinition = VersionedBpmnFiles.definitionsExists(basePath, path);
            if (hadPreviousDefinition) {
                previousDefinition = VersionedBpmnFiles.readDefinitionsFile(basePath, path);
            }

            archiveVersion = VersionedBpmnFiles.nextArchiveVersion(basePath, path);
            String body = VersionedBpmnFiles.readVersionFile(
                    basePath, VersionedBpmnFiles.ROOT_DEPLOY_APPLY, path, applyVersion);

            saveDefinitionAndArchive(path, archiveVersion, body);
            instanceService.postCreatedRawDefinition(path);
            putRawSucceeded = true;

            VersionedBpmnFiles.deleteDefinitionTree(basePath, VersionedBpmnFiles.ROOT_DEPLOY_APPLY, path);

            result.setVersion(applyVersion);
            result.setSuccess(true);
            log.info(
                    "Deploy apply ok: path={}, applyVersion={}, archiveVersion={}, created={}, deployApplyDeleted=true",
                    path, applyVersion, archiveVersion, !hadPreviousDefinition);
        } catch (Exception e) {
            String message = extractMessage(e);
            try {
                rollbackAfterFailure(path, archiveVersion, previousDefinition, hadPreviousDefinition, putRawSucceeded);
            } catch (Exception rollbackError) {
                log.error("Deploy apply rollback failed: path={}", path, rollbackError);
                message = message + "; rollback also failed: " + extractMessage(rollbackError);
            }
            result.setSuccess(false);
            result.setErrorMessage(message);
            log.warn("Deploy apply failed (rolled back): path={}, reason={}", path, message);
        }
        return result;
    }

    /**
     * definitions + archive 저장. ResourceManager 경유로 CachedResourceManager 캐시와 일치시킨다.
     */
    private void saveDefinitionAndArchive(String path, String archiveVersion, String body) throws Exception {
        DefaultResource versionResource = new DefaultResource(
                "/" + VersionedBpmnFiles.ROOT_ARCHIVE + "/" + path + "/" + archiveVersion + ".bpmn");
        resourceManager.save(versionResource, body != null ? body : "");

        DefaultResource definitionResource = new DefaultResource(
                VersionedBpmnFiles.ROOT_DEFINITIONS + "/" + path);
        resourceManager.save(definitionResource, body != null ? body : "");
    }

    private void rollbackAfterFailure(
            String path,
            String archiveVersionWritten,
            String previousDefinition,
            boolean hadPreviousDefinition,
            boolean putRawSucceeded) throws Exception {

        if (archiveVersionWritten != null) {
            DefaultResource versionResource = new DefaultResource(
                    "/" + VersionedBpmnFiles.ROOT_ARCHIVE + "/" + path + "/" + archiveVersionWritten + ".bpmn");
            resourceManager.delete(versionResource);
        }

        if (hadPreviousDefinition && previousDefinition != null) {
            DefaultResource definitionResource = new DefaultResource(
                    VersionedBpmnFiles.ROOT_DEFINITIONS + "/" + path);
            resourceManager.save(definitionResource, previousDefinition);
            if (putRawSucceeded) {
                instanceService.postCreatedRawDefinition(path);
            }
        } else if (!hadPreviousDefinition && putRawSucceeded) {
            DefaultResource definitionResource = new DefaultResource(
                    VersionedBpmnFiles.ROOT_DEFINITIONS + "/" + path);
            resourceManager.delete(definitionResource);
            log.warn(
                    "Rolled back new definition file for {}; event_mapping rows may remain until next successful deploy",
                    path);
        }
    }

    private static String extractMessage(Exception e) {
        if (e instanceof ResponseStatusException rse) {
            return rse.getReason() != null ? rse.getReason() : rse.getMessage();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
