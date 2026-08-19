package org.uengine.five.analytics.etl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.analytics.etl.entity.AnalyticsActivityDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsActorDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsDateDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessDefinitionDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessInstanceFact;
import org.uengine.five.analytics.etl.entity.AnalyticsTaskFact;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AnalyticsEtlService {

    private final AnalyticsSourceReader sourceReader;
    private final AnalyticsDateDimensionRepository dateRepository;
    private final AnalyticsProcessDefinitionDimensionRepository processDefinitionRepository;
    private final AnalyticsActivityDimensionRepository activityRepository;
    private final AnalyticsActorDimensionRepository actorRepository;
    private final AnalyticsProcessInstanceFactRepository processFactRepository;
    private final AnalyticsTaskFactRepository taskFactRepository;
    private final AnalyticsEtlTransformer transformer;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile AnalyticsEtlRunResult lastResult;
    private volatile String lastError;

    public AnalyticsEtlService(
            AnalyticsSourceReader sourceReader,
            AnalyticsDateDimensionRepository dateRepository,
            AnalyticsProcessDefinitionDimensionRepository processDefinitionRepository,
            AnalyticsActivityDimensionRepository activityRepository,
            AnalyticsActorDimensionRepository actorRepository,
            AnalyticsProcessInstanceFactRepository processFactRepository,
            AnalyticsTaskFactRepository taskFactRepository,
            AnalyticsEtlTransformer transformer) {
        this.sourceReader = sourceReader;
        this.dateRepository = dateRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.activityRepository = activityRepository;
        this.actorRepository = actorRepository;
        this.processFactRepository = processFactRepository;
        this.taskFactRepository = taskFactRepository;
        this.transformer = transformer;
    }

    @Transactional
    public AnalyticsEtlRunResult run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Analytics ETL is already running");
        }

        Instant startedAt = Instant.now();
        lastError = null;
        try {
            List<AnalyticsProcessInstanceSource> instances = sourceReader.processInstances();
            List<AnalyticsTaskSource> tasks = new ArrayList<>(sourceReader.tasks());
            tasks.sort(transformer.taskOrder());

            Map<Long, AnalyticsProcessInstanceSource> instanceById = new LinkedHashMap<>();
            Map<Long, List<AnalyticsTaskSource>> tasksByInstance = new LinkedHashMap<>();
            Map<String, AnalyticsProcessDefinitionDimension> processDimensions = new LinkedHashMap<>();
            Map<String, AnalyticsActivityDimension> activityDimensions = new LinkedHashMap<>();
            Map<String, AnalyticsActorDimension> actorDimensions = new LinkedHashMap<>();
            Map<Integer, AnalyticsDateDimension> dateDimensions = new LinkedHashMap<>();

            for (AnalyticsProcessInstanceSource instance : instances) {
                if (instance.instId() == null) {
                    continue;
                }
                instanceById.put(instance.instId(), instance);
                AnalyticsProcessDefinitionDimension dimension = transformer.processDimension(instance);
                processDimensions.put(dimension.getProcessKey(), dimension);
                addDate(dateDimensions, instance.startedDate());
                addDate(dateDimensions, instance.finishedDate());
                addDate(dateDimensions, instance.dueDate());
            }

            for (AnalyticsTaskSource task : tasks) {
                if (task.taskId() == null) {
                    continue;
                }
                if (task.instId() != null) {
                    tasksByInstance.computeIfAbsent(task.instId(), ignored -> new ArrayList<>()).add(task);
                }
                AnalyticsProcessDefinitionDimension processDimension = transformer.processDimension(task);
                processDimensions.putIfAbsent(processDimension.getProcessKey(), processDimension);
                AnalyticsActivityDimension activityDimension = transformer.activityDimension(task);
                activityDimensions.put(activityDimension.getActivityKey(), activityDimension);
                AnalyticsActorDimension actorDimension = transformer.actorDimension(task);
                if (actorDimension != null) {
                    actorDimensions.put(actorDimension.getActorKey(), actorDimension);
                }
                addDate(dateDimensions, task.startDate());
                addDate(dateDimensions, task.endDate());
                addDate(dateDimensions, task.dueDate());
            }

            List<AnalyticsProcessInstanceFact> processFacts = instances.stream()
                    .filter(instance -> instance.instId() != null)
                    .map(instance -> transformer.processFact(instance,
                            tasksByInstance.getOrDefault(instance.instId(), List.of()),
                            Date.from(startedAt)))
                    .toList();

            List<AnalyticsTaskFact> taskFacts = new ArrayList<>();
            Long previousInstanceId = null;
            Date previousTaskEnd = null;
            for (AnalyticsTaskSource task : tasks) {
                if (task.taskId() == null) {
                    continue;
                }
                if (!java.util.Objects.equals(previousInstanceId, task.instId())) {
                    previousInstanceId = task.instId();
                    previousTaskEnd = null;
                }
                taskFacts.add(transformer.taskFact(task, instanceById.get(task.instId()), previousTaskEnd));
                previousTaskEnd = task.endDate();
            }

            dateRepository.saveAll(dateDimensions.values());
            processDefinitionRepository.saveAll(processDimensions.values());
            activityRepository.saveAll(activityDimensions.values());
            actorRepository.saveAll(actorDimensions.values());
            processFactRepository.saveAll(processFacts);
            taskFactRepository.saveAll(taskFacts);

            lastResult = new AnalyticsEtlRunResult(startedAt, Instant.now(), instances.size(), tasks.size(),
                    processDimensions.size(), activityDimensions.size(), actorDimensions.size(),
                    dateDimensions.size(), processFacts.size(), taskFacts.size());
            return lastResult;
        } catch (RuntimeException exception) {
            lastError = exception.getMessage();
            throw exception;
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public AnalyticsEtlRunResult getLastResult() {
        return lastResult;
    }

    public String getLastError() {
        return lastError;
    }

    private void addDate(Map<Integer, AnalyticsDateDimension> dimensions, Date date) {
        AnalyticsDateDimension dimension = transformer.dateDimension(date);
        if (dimension != null) {
            dimensions.put(dimension.getDateKey(), dimension);
        }
    }
}
