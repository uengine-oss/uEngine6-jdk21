package org.uengine.five.analytics.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "uengine.analytics.etl", name = "enabled", havingValue = "true")
public class AnalyticsEtlScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEtlScheduler.class);

    private final AnalyticsEtlService etlService;

    public AnalyticsEtlScheduler(AnalyticsEtlService etlService) {
        this.etlService = etlService;
    }

    @Scheduled(
            initialDelayString = "${uengine.analytics.etl.initial-delay-ms:30000}",
            fixedDelayString = "${uengine.analytics.etl.interval-ms:60000}")
    public void run() {
        try {
            AnalyticsEtlRunResult result = etlService.run();
            log.info("Analytics ETL completed: {} process instances, {} tasks",
                    result.processFacts(), result.taskFacts());
        } catch (IllegalStateException exception) {
            log.debug("Analytics ETL schedule skipped: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Analytics ETL failed", exception);
        }
    }
}
