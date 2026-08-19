package org.uengine.five.analytics.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessDefinitionDimension;

public interface AnalyticsProcessDefinitionDimensionRepository
        extends JpaRepository<AnalyticsProcessDefinitionDimension, String> {
}
