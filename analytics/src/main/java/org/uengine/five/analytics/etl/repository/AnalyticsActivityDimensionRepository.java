package org.uengine.five.analytics.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.five.analytics.etl.entity.AnalyticsActivityDimension;

public interface AnalyticsActivityDimensionRepository extends JpaRepository<AnalyticsActivityDimension, String> {
}
