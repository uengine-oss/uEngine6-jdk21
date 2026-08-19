package org.uengine.five.rpa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RPA 워커/에이전트 및 프론트엔드 모니터링용 REST API.
 *
 * <ul>
 * <li>POST /rpa/poll — 워커·에이전트가 Job 폴링+claim</li>
 * <li>POST /rpa/jobs/{jobId}/trigger — 담당자의 수동 실행(WAITING → QUEUED)</li>
 * <li>POST /rpa/jobs/{jobId}/retry — 실패 Job 재실행(backToHere, 새 Job 큐잉)</li>
 * <li>POST /rpa/jobs/{jobId}/start — RUNNING 전환</li>
 * <li>POST /rpa/jobs/{jobId}/log — 실행 로그 append</li>
 * <li>POST /rpa/jobs/{jobId}/complete — 결과 보고(성공/실패)</li>
 * <li>POST/GET /rpa/jobs/{jobId}/frame — 라이브 뷰 프레임(jpeg, 메모리 보관)</li>
 * <li>POST/GET /rpa/jobs/{jobId}/video — 실행 녹화 영상(webm, 디스크 보관)</li>
 * <li>GET /rpa/jobs?instanceId= — 인스턴스별 Job 목록(모니터링)</li>
 * <li>GET /rpa/jobs/{jobId} — Job 상세(로그 포함)</li>
 * </ul>
 */
@RestController
public class RpaJobController {

    static final long MAX_FRAME_BYTES = 2L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 60L * 1024 * 1024;
    static final long LIVE_FRAME_TTL_MILLIS = 15_000;

    // 라이브 뷰 프레임은 잡 실행 중에만 의미가 있으므로 메모리에만 보관 (완료 시 제거)
    private static final Map<String, byte[]> liveFrames = new ConcurrentHashMap<>();
    private static final Map<String, Long> liveFrameTimes = new ConcurrentHashMap<>();

    @Autowired
    RpaJobService rpaJobService;

    @Autowired
    RpaValidationService rpaValidationService;

    /** 프론트엔드 작성 스크립트를 실제 업무 수행 없이 Robot Framework dry-run 검증한다. */
    @PostMapping("/rpa/validate")
    @SuppressWarnings("unchecked")
    public RpaValidationService.ValidationResult validate(@RequestBody Map<String, Object> body) {
        String script = body.get("script") == null ? "" : String.valueOf(body.get("script"));
        List<String> variables = body.get("variables") instanceof List<?>
                ? ((List<Object>) body.get("variables")).stream().map(String::valueOf).toList()
                : List.of();
        return rpaValidationService.validate(script, variables);
    }

    /** 워커/에이전트 폴링. Job 이 없으면 {} 반환. */
    @PostMapping("/rpa/poll")
    public Map<String, Object> poll(@RequestParam("agentId") String agentId,
            @RequestParam(value = "mode", defaultValue = RPAActivity.MODE_SERVER) String mode,
            @RequestParam(value = "user", required = false) String user) {

        // 폴링 시점에 시간 초과 Job 게으른 정리
        rpaJobService.expireOverdueJobs();

        RpaJobEntity job = rpaJobService.pollAndClaim(agentId, mode, user);
        if (job == null)
            return new HashMap<>();

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("instanceId", job.getInstanceId());
        response.put("activityName", job.getActivityName());
        response.put("mode", job.getMode());
        response.put("script", job.getScript());
        response.put("inputs", RpaJobService.parseJsonMap(job.getInputJson()));
        response.put("timeoutSeconds", job.getTimeoutSeconds());
        return response;
    }

    @PostMapping("/rpa/jobs/{jobId}/start")
    public void start(@PathVariable("jobId") String jobId) {
        rpaJobService.markRunning(jobId);
    }

    /** 담당자의 [RPA 실행] 버튼 — WAITING Job 을 QUEUED 로 전환해 에이전트가 가져가게 한다. */
    @PostMapping("/rpa/jobs/{jobId}/trigger")
    public Map<String, Object> trigger(@PathVariable("jobId") String jobId) {
        boolean triggered = rpaJobService.triggerJob(jobId);
        Map<String, Object> response = new HashMap<>();
        response.put("triggered", triggered);
        return response;
    }

    /** 담당자의 [재실행] 버튼 — 실패(FAILED/TIMEOUT)한 액티비티를 backToHere 로 재실행한다. */
    @PostMapping("/rpa/jobs/{jobId}/retry")
    public Map<String, Object> retry(@PathVariable("jobId") String jobId) {
        Map<String, Object> response = new HashMap<>();
        try {
            RpaJobEntity retried = rpaJobService.retryJob(jobId);
            response.put("retried", retried != null);
            if (retried != null)
                response.put("jobId", retried.getJobId());
        } catch (Exception e) {
            response.put("retried", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/rpa/jobs/{jobId}/log")
    public void log(@PathVariable("jobId") String jobId, @RequestBody String chunk) {
        rpaJobService.appendLog(jobId, chunk);
    }

    /**
     * 완료 보고. body: {"success": true, "result": {...}, "error": null}
     * result 맵의 값들이 RPAActivity 의 out 파라미터 매핑을 따라 ProcessVariable 에 할당된다.
     */
    @PostMapping("/rpa/jobs/{jobId}/complete")
    @SuppressWarnings("unchecked")
    public void complete(@PathVariable("jobId") String jobId, @RequestBody Map<String, Object> body) throws Exception {
        boolean success = Boolean.TRUE.equals(body.get("success"));
        Map<String, Object> result = body.get("result") instanceof Map ? (Map<String, Object>) body.get("result")
                : null;
        String error = body.get("error") != null ? String.valueOf(body.get("error")) : null;

        rpaJobService.completeJob(jobId, success, result, error);

        liveFrames.remove(jobId);
        liveFrameTimes.remove(jobId);
    }

    /** 에이전트가 올리는 라이브 뷰 프레임 (image/jpeg raw body). */
    @PostMapping("/rpa/jobs/{jobId}/frame")
    public void putFrame(@PathVariable("jobId") String jobId, @RequestBody byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_FRAME_BYTES)
            return;
        liveFrames.put(jobId, data);
        liveFrameTimes.put(jobId, System.currentTimeMillis());
    }

    /** 프론트엔드 라이브 뷰 — 최신 프레임 반환 (없으면 404). */
    @GetMapping("/rpa/jobs/{jobId}/frame")
    public ResponseEntity<byte[]> getFrame(@PathVariable("jobId") String jobId) {
        byte[] data = liveFrames.get(jobId);
        if (data == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).body(data);
    }

    /** 에이전트가 올리는 실행 녹화 영상 (video/webm raw body). */
    @PostMapping("/rpa/jobs/{jobId}/video")
    public void putVideo(@PathVariable("jobId") String jobId, @RequestBody byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_VIDEO_BYTES)
            return;
        Files.write(videoFile(jobId).toPath(), data);
    }

    /** 프론트엔드 실행이력 영상 재생 (없으면 404). */
    @GetMapping("/rpa/jobs/{jobId}/video")
    public ResponseEntity<byte[]> getVideo(@PathVariable("jobId") String jobId) throws IOException {
        File file = videoFile(jobId);
        if (!file.isFile())
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("video/webm"))
                .body(Files.readAllBytes(file.toPath()));
    }

    private File videoFile(String jobId) {
        String base = System.getenv("UENGINE_BASEPATH");
        if (base == null || base.isBlank())
            base = System.getProperty("java.io.tmpdir");
        File dir = new File(base, "rpa-videos");
        dir.mkdirs();
        return new File(dir, jobId.replaceAll("[^a-zA-Z0-9\\-]", "") + ".webm");
    }

    /** 프론트엔드 모니터링 — 인스턴스의 Job 목록 (로그는 tail 만 포함). */
    @GetMapping("/rpa/jobs")
    public List<Map<String, Object>> jobs(@RequestParam("instanceId") String instanceId) {
        return rpaJobService.getJobsByInstance(instanceId).stream().map(job -> toSummary(job, true)).toList();
    }

    @GetMapping("/rpa/jobs/{jobId}")
    public Map<String, Object> job(@PathVariable("jobId") String jobId) {
        RpaJobEntity job = rpaJobService.getJob(jobId);
        return job == null ? new HashMap<>() : toSummary(job, false);
    }

    private Map<String, Object> toSummary(RpaJobEntity job, boolean tailLogOnly) {
        Map<String, Object> map = new HashMap<>();
        map.put("jobId", job.getJobId());
        map.put("instanceId", job.getInstanceId());
        map.put("tracingTag", job.getTracingTag());
        map.put("definitionId", job.getDefinitionId());
        map.put("activityName", job.getActivityName());
        map.put("mode", job.getMode());
        map.put("targetUser", job.getTargetUser());
        map.put("status", job.getStatus());
        map.put("agentId", job.getAgentId());
        map.put("inputs", RpaJobService.parseJsonMap(job.getInputJson()));
        map.put("result", RpaJobService.parseJsonMap(job.getResultJson()));
        map.put("error", job.getError());
        map.put("createdDate", job.getCreatedDate());
        map.put("claimedDate", job.getClaimedDate());
        map.put("completedDate", job.getCompletedDate());

        String logText = job.getLogText() == null ? "" : job.getLogText();
        if (tailLogOnly && logText.length() > 4000)
            logText = "..." + logText.substring(logText.length() - 4000);
        map.put("log", logText);

        map.put("hasVideo", videoFile(job.getJobId()).isFile());
        Long frameAt = liveFrameTimes.get(job.getJobId());
        map.put("liveFrame", frameAt != null && System.currentTimeMillis() - frameAt < LIVE_FRAME_TTL_MILLIS);
        return map;
    }
}
