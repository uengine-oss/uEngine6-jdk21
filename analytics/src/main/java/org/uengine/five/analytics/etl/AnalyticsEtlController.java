package org.uengine.five.analytics.etl;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/analytics/etl")
public class AnalyticsEtlController {

    private final AnalyticsEtlService etlService;

    public AnalyticsEtlController(AnalyticsEtlService etlService) {
        this.etlService = etlService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> run() {
        try {
            return ResponseEntity.ok(etlService.run());
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", etlService.isRunning());
        status.put("lastResult", etlService.getLastResult());
        status.put("lastError", etlService.getLastError());
        return status;
    }
}
