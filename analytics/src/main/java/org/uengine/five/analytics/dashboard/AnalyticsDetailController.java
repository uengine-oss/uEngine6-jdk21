package org.uengine.five.analytics.dashboard;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/analytics")
public class AnalyticsDetailController {

    private final AnalyticsDetailService service;

    public AnalyticsDetailController(AnalyticsDetailService service) {
        this.service = service;
    }

    @GetMapping("/dimensions/processes")
    public Map<String, Object> processes() {
        return service.processes();
    }

    @GetMapping("/dashboard/tasks-by-department")
    public Map<String, Object> tasksByDepartment(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer month) {
        return service.tasksByDepartment(year, quarter, month);
    }

    @GetMapping("/analytics/process-performance")
    public Map<String, Object> processPerformance(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return service.processPerformance(year, quarter);
    }

    @GetMapping("/analytics/bottleneck")
    public Map<String, Object> bottleneck(
            @RequestParam(name = "proc_def_id", required = false) String processDefinitionId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return service.bottleneck(processDefinitionId, year, quarter);
    }

    @GetMapping("/analytics/monthly-trend")
    public Map<String, Object> monthlyTrend(@RequestParam(required = false) Integer year) {
        return service.monthlyTrend(year);
    }

    @GetMapping("/analytics/agent-vs-human")
    public Map<String, Object> agentVsHuman(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return service.agentVsHuman(year, quarter);
    }

    @GetMapping("/analytics/fte-heatmap")
    public Map<String, Object> fteHeatmap(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return service.fteHeatmap(year, quarter);
    }

    @GetMapping("/kpi/pipeline")
    public Map<String, Object> kpiPipeline() {
        return service.kpiPipeline();
    }

    @GetMapping("/kpi/domain-progress")
    public List<Map<String, Object>> kpiDomainProgress() {
        return service.kpiDomainProgress();
    }

    @GetMapping("/kpi/weekly-velocity")
    public List<Map<String, Object>> kpiWeeklyVelocity(
            @RequestParam(defaultValue = "10") int weeks) {
        return service.kpiWeeklyVelocity(weeks);
    }

    @GetMapping("/kpi/targets")
    public List<Map<String, Object>> kpiTargets() {
        return service.kpiTargets();
    }

    @PostMapping("/kpi/targets")
    public Map<String, Object> upsertKpiTarget(@RequestBody Map<String, Object> request) {
        return service.upsertKpiTarget(request);
    }
}
