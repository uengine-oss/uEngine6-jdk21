package org.uengine.five.analytics.etl;

import org.junit.jupiter.api.Test;
import org.uengine.five.analytics.etl.repository.AnalyticsActivityDimensionRepository;
import org.uengine.five.analytics.etl.repository.AnalyticsActorDimensionRepository;
import org.uengine.five.analytics.etl.repository.AnalyticsDateDimensionRepository;
import org.uengine.five.analytics.etl.repository.AnalyticsProcessDefinitionDimensionRepository;
import org.uengine.five.analytics.etl.repository.AnalyticsProcessInstanceFactRepository;
import org.uengine.five.analytics.etl.repository.AnalyticsTaskFactRepository;
import org.uengine.five.analytics.source.AnalyticsProcessInstanceSource;
import org.uengine.five.analytics.source.AnalyticsSourceReader;
import org.uengine.five.analytics.source.AnalyticsTaskSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsEtlServiceTest {

    @Test
    void loadsDimensionsBeforeIdempotentFacts() {
        AnalyticsSourceReader sourceReader = mock(AnalyticsSourceReader.class);
        AnalyticsDateDimensionRepository dates = mock(AnalyticsDateDimensionRepository.class);
        AnalyticsProcessDefinitionDimensionRepository processes = mock(AnalyticsProcessDefinitionDimensionRepository.class);
        AnalyticsActivityDimensionRepository activities = mock(AnalyticsActivityDimensionRepository.class);
        AnalyticsActorDimensionRepository actors = mock(AnalyticsActorDimensionRepository.class);
        AnalyticsProcessInstanceFactRepository processFacts = mock(AnalyticsProcessInstanceFactRepository.class);
        AnalyticsTaskFactRepository taskFacts = mock(AnalyticsTaskFactRepository.class);

        AnalyticsProcessInstanceSource instance = new AnalyticsProcessInstanceSource(
                7L, "definition", "1", "Definition", null,
                Date.from(Instant.parse("2026-08-20T00:00:00Z")), null, null, null,
                "RUNNING", false, false, false, null, null, null);

        AnalyticsTaskSource first = worklist(1L, "2026-08-20T00:10:00Z", "2026-08-20T00:20:00Z");
        AnalyticsTaskSource second = worklist(2L, "2026-08-20T00:25:00Z", "2026-08-20T00:30:00Z");

        when(sourceReader.processInstances()).thenReturn(List.of(instance));
        when(sourceReader.tasks()).thenReturn(List.of(second, first));

        AnalyticsEtlService service = new AnalyticsEtlService(sourceReader, dates,
                processes, activities, actors, processFacts, taskFacts,
                new AnalyticsEtlTransformer(ZoneId.of("Asia/Seoul")));

        AnalyticsEtlRunResult result = service.run();

        assertThat(result.sourceProcessInstances()).isEqualTo(1);
        assertThat(result.sourceTasks()).isEqualTo(2);
        assertThat(result.processFacts()).isEqualTo(1);
        assertThat(result.taskFacts()).isEqualTo(2);
        verify(dates).saveAll(any());
        verify(processes).saveAll(any());
        verify(activities).saveAll(any());
        verify(processFacts).saveAll(any());
        verify(taskFacts).saveAll(any());
        assertThat(service.isRunning()).isFalse();
        assertThat(service.getLastError()).isNull();
    }

    private AnalyticsTaskSource worklist(Long id, String start, String end) {
        Date startDate = Date.from(Instant.parse(start));
        Date endDate = Date.from(Instant.parse(end));
        return new AnalyticsTaskSource(
                id, 7L, 7L, "definition", "1", "Definition",
                "activity-" + id, null, "Task " + id, null, null,
                "user", null, null, null,
                startDate, endDate, null, endDate,
                "COMPLETED", null, null, null, false);
    }
}
