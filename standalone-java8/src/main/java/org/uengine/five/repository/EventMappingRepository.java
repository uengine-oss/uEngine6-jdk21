package org.uengine.five.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.five.entity.EventMappingEntity;

@RepositoryRestResource(collectionResourceRel = "event-mappings", path = "event-mappings")
public interface EventMappingRepository extends JpaRepository<EventMappingEntity, Long> {

    // @Query("SELECT e.definitionId FROM EventMappingEntity e WHERE e.eventName =
    // :eventName")
    // public String findDefinitionIdByEventName(String eventName);

    @Query("SELECT e FROM EventMappingEntity e WHERE e.eventName = :eventName")
    public EventMappingEntity findEventMappingByEventName(String eventName);

    /** 배포 시 멱등 upsert 용 — event_name(UNIQUE) 으로 기존 매핑 조회. */
    EventMappingEntity findByEventName(String eventName);
}
