package org.uengine.five.analytics.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.five.analytics.etl.entity.AnalyticsTaskFact;

public interface AnalyticsTaskFactRepository extends JpaRepository<AnalyticsTaskFact, Long> {
}
