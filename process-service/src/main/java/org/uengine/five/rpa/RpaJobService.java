package org.uengine.five.rpa;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.framework.ProcessTransactional;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.kernel.Activity;
import org.uengine.kernel.ProcessInstance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RPA Job 큐 관리 + Job 결과의 프로세스 엔진 반영.
 */
@Service
public class RpaJobService {

    private static final Logger log = LoggerFactory.getLogger(RpaJobService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    RpaJobRepository rpaJobRepository;

    @Autowired
    InstanceServiceImpl instanceService;

    /** RPAActivity.executeActivity 에서 호출 — Job 생성(큐잉). */
    public RpaJobEntity createJob(RPAActivity activity, ProcessInstance instance, Map<String, Object> inputs)
            throws Exception {

        RpaJobEntity job = new RpaJobEntity();
        job.setJobId(UUID.randomUUID().toString());
        job.setInstanceId(instance.getInstanceId());
        job.setTracingTag(activity.getTracingTag());
        job.setExecutionScope(instance.getExecutionScopeContext() != null
                ? instance.getExecutionScopeContext().getExecutionScope()
                : null);
        job.setLoopIndex(-1);
        job.setDefinitionId(instance.getProcessDefinition().getId());
        job.setActivityName(activity.getName());

        String mode = RPAActivity.MODE_CLIENT.equalsIgnoreCase(activity.getExecutionType())
                ? RPAActivity.MODE_CLIENT
                : RPAActivity.MODE_SERVER;
        job.setMode(mode);
        job.setTargetUser(activity.getTargetUser());
        // autoStart 가 아니면 담당자가 [RPA 실행] 을 누를 때까지 WAITING (폴링 대상 제외)
        job.setStatus(activity.isAutoStart() ? RpaJobEntity.STATUS_QUEUED : RpaJobEntity.STATUS_WAITING);
        job.setScript(activity.getRobotScript());
        job.setInputJson(objectMapper.writeValueAsString(inputs));
        job.setTimeoutSeconds(activity.getTimeoutSeconds() > 0 ? activity.getTimeoutSeconds() : 600);
        job.setCreatedDate(new Date());
        job.setLogText("");

        rpaJobRepository.save(job);

        log.info("[RPA] job created: {} status={} mode={} instance={} scope={} activity={}({})", job.getJobId(),
                job.getStatus(), mode, job.getInstanceId(), job.getExecutionScope(), job.getActivityName(),
                job.getTracingTag());

        return job;
    }

    /** 담당자의 수동 실행 트리거 — WAITING 상태의 Job 을 QUEUED 로 전환해 에이전트 폴링 대상에 넣는다. */
    @Transactional
    public boolean triggerJob(String jobId) {
        RpaJobEntity job = rpaJobRepository.findById(jobId).orElse(null);
        if (job == null || !RpaJobEntity.STATUS_WAITING.equals(job.getStatus()))
            return false;
        job.setStatus(RpaJobEntity.STATUS_QUEUED);
        rpaJobRepository.save(job);
        log.info("[RPA] job triggered by user: {} instance={} activity={}", jobId, job.getInstanceId(),
                job.getActivityName());
        return true;
    }

    /**
     * 담당자의 [재실행] — 실패(FAILED/TIMEOUT)한 Job 의 액티비티를 엔진 backToHere 로
     * 되돌려 재실행한다. executeActivity 가 새 Job 을 만들므로(수동 모드면 WAITING),
     * 버튼 클릭 자체가 담당자 승인이니 곧바로 QUEUED 로 전환해 에이전트가 가져가게 한다.
     * 기존 실패 Job 은 이력으로 남는다.
     *
     * @return 새로 큐잉된 Job, 재실행 불가 상태면 null
     */
    @ProcessTransactional
    @Transactional(rollbackFor = { Exception.class })
    public RpaJobEntity retryJob(String jobId) throws Exception {
        RpaJobEntity failed = rpaJobRepository.findById(jobId).orElse(null);
        if (failed == null || !(RpaJobEntity.STATUS_FAILED.equals(failed.getStatus())
                || RpaJobEntity.STATUS_TIMEOUT.equals(failed.getStatus())))
            return null;

        ProcessInstance instance = instanceService.getProcessInstanceLocal(failed.getInstanceId());
        String tracingTag = failed.getTracingTag();
        if (tracingTag.contains(":")) {
            instance.setExecutionScope(tracingTag.split(":")[1]);
            tracingTag = tracingTag.split(":")[0];
        }
        instance.getProcessDefinition().getActivity(tracingTag).backToHere(instance);

        // backToHere → executeActivity(createJob) 가 만든 새 Job 을 찾아 즉시 큐잉
        RpaJobEntity retried = null;
        for (RpaJobEntity j : rpaJobRepository.findByInstanceIdOrderByCreatedDateAsc(failed.getInstanceId())) {
            if (failed.getTracingTag().equals(j.getTracingTag()) && !jobId.equals(j.getJobId())
                    && (RpaJobEntity.STATUS_WAITING.equals(j.getStatus())
                            || RpaJobEntity.STATUS_QUEUED.equals(j.getStatus())))
                retried = j; // createdDate asc — 마지막 것이 방금 생성된 Job
        }
        if (retried != null && RpaJobEntity.STATUS_WAITING.equals(retried.getStatus())) {
            retried.setStatus(RpaJobEntity.STATUS_QUEUED);
            rpaJobRepository.save(retried);
        }
        log.info("[RPA] job retried: old={} new={} instance={} activity={}", jobId,
                retried != null ? retried.getJobId() : "?", failed.getInstanceId(), failed.getActivityName());
        return retried;
    }

    /**
     * 에이전트/워커 폴링 — 조건에 맞는 QUEUED Job 하나를 원자적으로 claim 한다.
     *
     * @param mode server | client
     * @param user client 모드에서 에이전트 사용자 endpoint (server 모드는 무시)
     * @return claim 된 Job, 없으면 null
     */
    @Transactional
    public RpaJobEntity pollAndClaim(String agentId, String mode, String user) {

        Optional<RpaJobEntity> candidate;
        if (RPAActivity.MODE_CLIENT.equals(mode) && user != null && !user.isEmpty()) {
            candidate = rpaJobRepository.findFirstByStatusAndModeAndTargetUserOrderByCreatedDateAsc(
                    RpaJobEntity.STATUS_QUEUED, mode, user);
            if (candidate.isEmpty()) {
                // targetUser 미지정 client Job 은 아무 에이전트나 수행 가능
                candidate = rpaJobRepository.findFirstByStatusAndModeAndTargetUserOrderByCreatedDateAsc(
                        RpaJobEntity.STATUS_QUEUED, mode, null);
            }
        } else {
            candidate = rpaJobRepository.findFirstByStatusAndModeOrderByCreatedDateAsc(
                    RpaJobEntity.STATUS_QUEUED, mode);
        }

        if (candidate.isEmpty())
            return null;

        RpaJobEntity job = candidate.get();
        int claimed = rpaJobRepository.claim(job.getJobId(), agentId);
        if (claimed == 0)
            return null; // 다른 에이전트가 선점

        job.setStatus(RpaJobEntity.STATUS_CLAIMED);
        job.setAgentId(agentId);
        job.setClaimedDate(new Date());
        return job;
    }

    @Transactional
    public void markRunning(String jobId) {
        rpaJobRepository.findById(jobId).ifPresent(job -> {
            if (RpaJobEntity.STATUS_CLAIMED.equals(job.getStatus())) {
                job.setStatus(RpaJobEntity.STATUS_RUNNING);
                rpaJobRepository.save(job);
            }
        });
    }

    @Transactional
    public void appendLog(String jobId, String chunk) {
        if (chunk == null || chunk.isEmpty())
            return;
        rpaJobRepository.findById(jobId).ifPresent(job -> {
            String current = job.getLogText() == null ? "" : job.getLogText();
            // 로그 폭주 방지 (최대 200KB 유지)
            String merged = current + chunk;
            if (merged.length() > 200_000)
                merged = merged.substring(merged.length() - 200_000);
            job.setLogText(merged);
            rpaJobRepository.save(job);
        });
    }

    /**
     * Job 완료 보고 처리 — 성공이면 결과를 ProcessVariable 로 매핑하고 프로세스를 진행,
     * 실패면 액티비티를 fault 상태로 만든다.
     */
    @ProcessTransactional
    @Transactional(rollbackFor = { Exception.class })
    public void completeJob(String jobId, boolean success, Map<String, Object> result, String error) throws Exception {

        RpaJobEntity job = rpaJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No such RPA job: " + jobId));

        if (RpaJobEntity.STATUS_DONE.equals(job.getStatus()) || RpaJobEntity.STATUS_FAILED.equals(job.getStatus())
                || RpaJobEntity.STATUS_TIMEOUT.equals(job.getStatus())) {
            log.warn("[RPA] job {} already terminal ({}), ignoring duplicate completion", jobId, job.getStatus());
            return;
        }

        job.setCompletedDate(new Date());
        job.setResultJson(result != null ? objectMapper.writeValueAsString(result) : null);
        job.setError(error);
        job.setStatus(success ? RpaJobEntity.STATUS_DONE : RpaJobEntity.STATUS_FAILED);
        rpaJobRepository.save(job);

        ProcessInstance instance = instanceService.getProcessInstanceLocal(job.getInstanceId());
        String originalScope = instance.getExecutionScopeContext() != null
                ? instance.getExecutionScopeContext().getExecutionScope()
                : null;
        if (job.getExecutionScope() != null) {
            try {
                instance.setExecutionScope(job.getExecutionScope());
            } catch (RuntimeException missingScope) {
                // 비동기 RPA 콜백은 새 반복 스코프가 영속화되기 전에 도착할 수 있다.
                // 작업 생성 시 저장한 번호와 동일한 스코프를 다시 등록해 그 반복만 완료한다.
                Activity scopeRoot = rpaActivityParent(instance, job.getTracingTag());
                instance.issueNewExecutionScope(scopeRoot, scopeRoot, job.getExecutionScope());
                instance.setExecutionScope(job.getExecutionScope());
            }
        }

        Activity activity = instance.getProcessDefinition().getActivity(job.getTracingTag());

        if (!(activity instanceof RPAActivity)) {
            log.warn("[RPA] activity {} of instance {} is not an RPAActivity — skip engine callback",
                    job.getTracingTag(), job.getInstanceId());
            instance.setExecutionScope(originalScope);
            return;
        }

        RPAActivity rpaActivity = (RPAActivity) activity;

        if (!instance.isRunning(rpaActivity.getTracingTag()) && job.getExecutionScope() == null) {
            log.warn("[RPA] activity {}({}) is not running for instance {} — result recorded, engine skip",
                    rpaActivity.getName(), rpaActivity.getTracingTag(), job.getInstanceId());
            instance.setExecutionScope(originalScope);
            return;
        }

        if (success) {
            rpaActivity.onJobResult(instance, result);
            log.info("[RPA] job {} done — process {} advanced past {}", jobId, job.getInstanceId(),
                    job.getTracingTag());
        } else {
            rpaActivity.onJobFailed(instance, error);
            log.info("[RPA] job {} failed — activity {} faulted: {}", jobId, job.getTracingTag(), error);
        }
        // onJobResult 이후 등록된 서브프로세스 완료 훅도 동일한 실행 스코프에서 실행된다.
        // 여기서 루트로 복원하면 멀티 인스턴스 서브프로세스의 완료 처리가 실패한다.
    }

    /** 실행 제한 시간을 초과한 Job 들을 실패 처리 (폴링 시 게으른 수행). */
    public void expireOverdueJobs() {
        List<RpaJobEntity> active = rpaJobRepository
                .findByStatusIn(List.of(RpaJobEntity.STATUS_CLAIMED, RpaJobEntity.STATUS_RUNNING));

        long now = System.currentTimeMillis();
        for (RpaJobEntity job : active) {
            Date started = job.getClaimedDate() != null ? job.getClaimedDate() : job.getCreatedDate();
            if (started == null)
                continue;
            long limitMs = (job.getTimeoutSeconds() > 0 ? job.getTimeoutSeconds() : 600) * 1000L;
            if (now - started.getTime() > limitMs) {
                try {
                    completeJob(job.getJobId(), false, null,
                            "RPA job timed out after " + job.getTimeoutSeconds() + "s");
                    // completeJob 이 FAILED 로 기록하므로 TIMEOUT 상태로 덮어쓴다
                    rpaJobRepository.findById(job.getJobId()).ifPresent(j -> {
                        j.setStatus(RpaJobEntity.STATUS_TIMEOUT);
                        rpaJobRepository.save(j);
                    });
                } catch (Exception e) {
                    log.error("[RPA] failed to expire job " + job.getJobId(), e);
                }
            }
        }
    }

    public List<RpaJobEntity> getJobsByInstance(String instanceId) {
        return rpaJobRepository.findByInstanceIdOrderByCreatedDateAsc(instanceId);
    }

    public RpaJobEntity getJob(String jobId) {
        return rpaJobRepository.findById(jobId).orElse(null);
    }

    public static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty())
            return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Activity rpaActivityParent(ProcessInstance instance, String tracingTag) throws Exception {
        Activity activity = instance.getProcessDefinition().getActivity(tracingTag);
        return activity.getParentActivity();
    }
}
