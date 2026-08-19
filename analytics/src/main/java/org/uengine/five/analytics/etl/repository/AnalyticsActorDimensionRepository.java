package org.uengine.five.analytics.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.five.analytics.etl.entity.AnalyticsActorDimension;

public interface AnalyticsActorDimensionRepository extends JpaRepository<AnalyticsActorDimension, String> {
}
