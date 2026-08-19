package org.uengine.five.analytics.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.five.analytics.etl.entity.AnalyticsDateDimension;

public interface AnalyticsDateDimensionRepository extends JpaRepository<AnalyticsDateDimension, Integer> {
}
