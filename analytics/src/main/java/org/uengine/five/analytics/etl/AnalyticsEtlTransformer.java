package org.uengine.five.analytics.etl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.five.analytics.etl.entity.AnalyticsActivityDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsActorDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsDateDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessDefinitionDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessInstanceFact;
import org.uengine.five.analytics.etl.entity.AnalyticsTaskFact;
import org.uengine.five.analytics.source.AnalyticsProcessInstanceSource;
import org.uengine.five.analytics.source.AnalyticsTaskSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Component
public class AnalyticsEtlTransformer {

    private final ZoneId zoneId;

    @Autowired
    public AnalyticsEtlTransformer(@Value("${uengine.analytics.etl.time-zone:Asia/Seoul}") String timeZone) {
        this(ZoneId.of(timeZone));
    }

    AnalyticsEtlTransformer(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public String processKey(String definitionId, String definitionVersionId) {
        return stableKey("process", definitionId, definitionVersionId);
    }

    public String activityKey(AnalyticsTaskSource worklist) {
        return stableKey("activity", worklist.defId(), worklist.defVerId(),
                worklist.absoluteTracingTag(), worklist.tracingTag(), worklist.title());
    }

    public String actorKey(AnalyticsTaskSource worklist) {
        if (!hasText(worklist.endpoint()) && !hasText(worklist.resourceName())
                && !hasText(worklist.groupCode()) && !hasText(worklist.roleName())) {
            return null;
        }
        return stableKey("actor", worklist.endpoint(), worklist.groupCode(), worklist.roleName());
    }

    public AnalyticsProcessDefinitionDimension processDimension(AnalyticsProcessInstanceSource instance) {
        return new AnalyticsProcessDefinitionDimension(
                processKey(instance.defId(), instance.defVerId()),
                instance.defId(), instance.defVerId(), instance.defName(), instance.defPath());
    }

    public AnalyticsProcessDefinitionDimension processDimension(AnalyticsTaskSource worklist) {
        return new AnalyticsProcessDefinitionDimension(
                processKey(worklist.defId(), worklist.defVerId()),
                worklist.defId(), worklist.defVerId(), worklist.defName(), null);
    }

    public AnalyticsActivityDimension activityDimension(AnalyticsTaskSource worklist) {
        return new AnalyticsActivityDimension(
                activityKey(worklist),
                processKey(worklist.defId(), worklist.defVerId()),
                worklist.tracingTag(), worklist.absoluteTracingTag(), worklist.title(),
                worklist.activityType(), worklist.tool());
    }

    public AnalyticsActorDimension actorDimension(AnalyticsTaskSource worklist) {
        String key = actorKey(worklist);
        return key == null ? null : new AnalyticsActorDimension(
                key, worklist.endpoint(), worklist.resourceName(),
                worklist.groupCode(), worklist.roleName());
    }

    public AnalyticsDateDimension dateDimension(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = Instant.ofEpochMilli(date.getTime()).atZone(zoneId).toLocalDate();
        int key = localDate.getYear() * 10_000 + localDate.getMonthValue() * 100 + localDate.getDayOfMonth();
        int dayOfWeek = localDate.getDayOfWeek().getValue();
        return new AnalyticsDateDimension(key, localDate, localDate.getYear(),
                (localDate.getMonthValue() - 1) / 3 + 1, localDate.getMonthValue(),
                localDate.getDayOfMonth(), localDate.get(WeekFields.ISO.weekOfWeekBasedYear()),
                dayOfWeek, dayOfWeek >= 6);
    }

    public Integer dateKey(Date date) {
        AnalyticsDateDimension dimension = dateDimension(date);
        return dimension == null ? null : dimension.getDateKey();
    }

    public AnalyticsTaskFact taskFact(AnalyticsTaskSource worklist, AnalyticsProcessInstanceSource instance,
                                      Date previousTaskEnd) {
        AnalyticsTaskFact fact = new AnalyticsTaskFact();
        fact.setTaskId(worklist.taskId());
        fact.setProcessInstanceId(worklist.instId());
        fact.setRootProcessInstanceId(rootInstanceId(worklist));
        fact.setProcessKey(processKey(worklist.defId(), worklist.defVerId()));
        fact.setActivityKey(activityKey(worklist));
        fact.setActorKey(actorKey(worklist));
        fact.setStartDateKey(dateKey(worklist.startDate()));
        fact.setEndDateKey(dateKey(worklist.endDate()));
        fact.setStartedAt(worklist.startDate());
        fact.setFinishedAt(worklist.endDate());
        fact.setDurationSeconds(secondsBetween(worklist.startDate(), worklist.endDate()));
        fact.setWaitFromPreviousSeconds(secondsBetween(previousTaskEnd, worklist.startDate()));
        fact.setLeadFromProcessSeconds(instance == null ? null
                : secondsBetween(instance.startedDate(), worklist.endDate()));
        fact.setStatus(worklist.status());
        fact.setDecision(worklist.decision());
        fact.setDecisionReason(worklist.reason());
        fact.setPriority(worklist.priority());
        fact.setDelegated(worklist.delegated());
        boolean human = isHuman(worklist);
        fact.setHumanTask(human);
        fact.setAutomatedTask(!human && (hasText(worklist.tool()) || hasText(worklist.activityType())));
        fact.setReworkTask(isRework(worklist));
        fact.setSourceUpdatedAt(latest(worklist.saveDate(), worklist.endDate(), worklist.startDate()));
        return fact;
    }

    public AnalyticsProcessInstanceFact processFact(AnalyticsProcessInstanceSource instance,
                                                      List<AnalyticsTaskSource> tasks) {
        return processFact(instance, tasks, new Date());
    }

    public AnalyticsProcessInstanceFact processFact(AnalyticsProcessInstanceSource instance,
                                                      List<AnalyticsTaskSource> tasks,
                                                      Date extractedAt) {
        AnalyticsProcessInstanceFact fact = new AnalyticsProcessInstanceFact();
        fact.setProcessInstanceId(instance.instId());
        fact.setRootProcessInstanceId(instance.rootInstId() == null
                ? instance.instId() : instance.rootInstId());
        fact.setProcessKey(processKey(instance.defId(), instance.defVerId()));
        fact.setStartDateKey(dateKey(instance.startedDate()));
        fact.setEndDateKey(dateKey(instance.finishedDate()));
        fact.setStartedAt(instance.startedDate());
        fact.setFinishedAt(instance.finishedDate());
        fact.setDurationSeconds(secondsBetween(instance.startedDate(),
                instance.finishedDate() == null ? extractedAt : instance.finishedDate()));
        fact.setStatus(instance.status());
        fact.setDeleted(instance.deleted());
        fact.setSubprocess(instance.subprocess());
        fact.setEventHandler(instance.eventHandler());
        fact.setInitiator(instance.initiator());
        fact.setInitiatorGroupCode(instance.initiatorGroupCode());
        fact.setTotalTaskCount(tasks.size());
        fact.setCompletedTaskCount(count(tasks, task -> statusIs(task, "COMPLETED")));
        fact.setActiveTaskCount(count(tasks, task -> statusIs(task, "NEW") || statusIs(task, "RUNNING")));
        fact.setCancelledTaskCount(count(tasks, task -> statusIs(task, "CANCELLED")));
        fact.setHumanTaskCount(count(tasks, this::isHuman));
        fact.setAutomatedTaskCount(count(tasks,
                task -> !isHuman(task) && (hasText(task.tool()) || hasText(task.activityType()))));
        fact.setReworkTaskCount(count(tasks, this::isRework));
        fact.setSourceUpdatedAt(latest(instance.modDate(), instance.finishedDate(), instance.startedDate()));
        return fact;
    }

    public Comparator<AnalyticsTaskSource> taskOrder() {
        return Comparator.comparing(AnalyticsTaskSource::instId,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AnalyticsTaskSource::startDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AnalyticsTaskSource::taskId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Long rootInstanceId(AnalyticsTaskSource worklist) {
        return worklist.rootInstId() == null ? worklist.instId() : worklist.rootInstId();
    }

    private boolean isHuman(AnalyticsTaskSource task) {
        return hasText(task.endpoint()) || hasText(task.resourceName());
    }

    private boolean isRework(AnalyticsTaskSource task) {
        String decision = task.decision();
        return hasText(decision) && (decision.toUpperCase(Locale.ROOT).contains("RETURN")
                || decision.toUpperCase(Locale.ROOT).contains("BACKTOHERE"));
    }

    private boolean statusIs(AnalyticsTaskSource task, String status) {
        return status.equalsIgnoreCase(task.status());
    }

    private int count(List<AnalyticsTaskSource> tasks,
                      java.util.function.Predicate<AnalyticsTaskSource> predicate) {
        return (int) tasks.stream().filter(predicate).count();
    }

    private Long secondsBetween(Date start, Date end) {
        if (start == null || end == null) {
            return null;
        }
        return Math.max(0L, (end.getTime() - start.getTime()) / 1_000L);
    }

    private Date latest(Date... dates) {
        Date latest = null;
        for (Date date : dates) {
            if (date != null && (latest == null || date.after(latest))) {
                latest = date;
            }
        }
        return latest;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim());
    }

    private String stableKey(String namespace, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(namespace.getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                digest.update((byte) 0);
                if (value != null) {
                    digest.update(value.trim().getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] hash = digest.digest();
            StringBuilder key = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                key.append(String.format("%02x", hash[index]));
            }
            return key.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
