package org.uengine.five.analytics.etl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "uengine.analytics.etl", name = "enabled", havingValue = "true")
public class AnalyticsEtlSchedulingConfiguration {
}
