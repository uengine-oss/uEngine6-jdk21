package org.uengine.five.analytics.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessInstanceFact;

public interface AnalyticsProcessInstanceFactRepository extends JpaRepository<AnalyticsProcessInstanceFact, Long> {
}
