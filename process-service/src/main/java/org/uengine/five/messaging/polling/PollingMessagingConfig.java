package org.uengine.five.messaging.polling;

import java.util.Set;

import javax.sql.DataSource;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.messaging.EventPublisher;

/**
 * 폴링 전략 활성화. {@code uengine.messaging.mode=polling} 일 때만 모든 Bean 등록.
 *
 * <p>스케줄러는 BPMN TimerEvent 가 이미 사용 중인 {@link StdSchedulerFactory} (SchedulerConfig
 * 에서 등록) 를 그대로 공유한다. JobGroup 을 {@code "uengine-inbox"} 로 분리해 TimerEvent 의
 * {@code "uengine"} 그룹과 격리.
 *
 * <p>RAMJobStore (기본) 사용 — 앱 재기동 시 잡 등록은 사라지지만 본 메서드가 매 기동 시
 * 다시 등록하므로 동작에 영향 없음. Inbox row 자체는 DB 에 영속되어 손실 없음.
 */
@Configuration
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class PollingMessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(PollingMessagingConfig.class);

    private static final String JOB_GROUP = "uengine-inbox";
    private static final JobKey POLL_JOB_KEY = JobKey.jobKey("inbox-poll", JOB_GROUP);
    private static final JobKey TTL_JOB_KEY  = JobKey.jobKey("inbox-ttl",  JOB_GROUP);

    @Value("${uengine.messaging.polling.interval-ms:1000}")
    private long intervalMs;

    @Value("${uengine.messaging.polling.inbox-ttl-cron:0 0 3 * * ?}")
    private String ttlCron;

    @Bean
    public EventPublisher inboxEventPublisher(EventInboxRepository repo, JdbcTemplate jdbc) {
        return new org.uengine.five.messaging.polling.InboxEventPublisher(repo, jdbc);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "uengine.messaging.polling.pg-notify.enabled", havingValue = "true")
    public PgNotifyListener pgNotifyListener(DataSource dataSource) {
        PgNotifyListener listener = new PgNotifyListener(dataSource, Set.of("bpm_bpm_brodcast"));
        listener.start();
        return listener;
    }

    @Bean
    @ConditionalOnProperty(name = "uengine.messaging.polling.pg-notify.enabled", havingValue = "true")
    public FilterRegistrationBean<SseAccessTokenQueryFilter> sseAccessTokenQueryFilter() {
        FilterRegistrationBean<SseAccessTokenQueryFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SseAccessTokenQueryFilter());
        reg.addUrlPatterns("/events/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /**
     * 앱 기동 시 Quartz Scheduler 에 inbox 폴링/TTL 잡을 등록한다. 기존 SchedulerConfig 가
     * 만든 Scheduler 인스턴스를 공유하므로 BPMN TimerEvent 와 같은 thread pool/store 를 사용.
     */
    @Bean
    public InboxQuartzRegistrar inboxQuartzRegistrar(StdSchedulerFactory schedulerFactory) throws SchedulerException {
        Scheduler sched = schedulerFactory.getScheduler();

        scheduleInboxPoll(sched);
        scheduleTtlCleanup(sched);

        if (!sched.isStarted()) {
            sched.start();
        }
        log.info("[inbox-quartz] registered: poll(every {}ms) + ttl(cron {})", intervalMs, ttlCron);
        return new InboxQuartzRegistrar(sched);
    }

    private void scheduleInboxPoll(Scheduler sched) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(InboxPollJob.class)
                .withIdentity(POLL_JOB_KEY)
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(POLL_JOB_KEY)
                .withIdentity("inbox-poll-trigger", JOB_GROUP)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(intervalMs)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();

        // 동일 키 잡이 남아있을 수 있어 재등록 시 안전하게 교체
        sched.scheduleJob(job, Set.of(trigger), true);
    }

    private void scheduleTtlCleanup(Scheduler sched) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(InboxTtlCleanupQuartzJob.class)
                .withIdentity(TTL_JOB_KEY)
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(TTL_JOB_KEY)
                .withIdentity("inbox-ttl-trigger", JOB_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(ttlCron))
                .build();

        sched.scheduleJob(job, Set.of(trigger), true);
    }

    /**
     * Bean 컨테이너 종료 시 등록한 잡을 제거. Scheduler 자체는 다른 사용자(TimerEvent) 가
     * 있을 수 있으므로 shutdown 하지 않음.
     */
    public static class InboxQuartzRegistrar {
        private final Scheduler scheduler;

        public InboxQuartzRegistrar(Scheduler scheduler) {
            this.scheduler = scheduler;
        }

        public void close() {
            try {
                scheduler.deleteJob(POLL_JOB_KEY);
                scheduler.deleteJob(TTL_JOB_KEY);
            } catch (Exception ignore) {
            }
        }
    }
}
