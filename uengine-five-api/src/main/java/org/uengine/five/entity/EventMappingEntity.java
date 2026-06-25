package org.uengine.five.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BPM_EVENT_MAPPING",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_mapping_event_name", columnNames = "event_name"))
@SequenceGenerator(
    name = "event_mapping_seq_gen",
    sequenceName = "SEQ_BPM_EVENT_MAPPING",
    allocationSize = 50
)
public class EventMappingEntity {

    /** 테이블 surrogate PK. 업무 식별은 {@link #eventName} (UNIQUE) 로 한다. */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_mapping_seq_gen")
    @Column(name = "id")
    private Long id;

    /** 외부/내부 이벤트 식별자(이벤트명). 매핑 조회의 업무 키 — UNIQUE. */
    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "definition_id")
    private String definitionId;

    @Column(name = "correlation_key")
    private String correlationKey;

    @Column(name = "tracing_tag")
    private String tracingTag;

    @Column(name = "is_start_event")
    private Boolean isStartEvent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getTracingTag() {
        return tracingTag;
    }

    public void setTracingTag(String tracingTag) {
        this.tracingTag = tracingTag;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public Boolean isStartEvent() {
        return isStartEvent;
    }

    public void setIsStartEvent(Boolean isStartEvent) {
        this.isStartEvent = isStartEvent;
    }

}
