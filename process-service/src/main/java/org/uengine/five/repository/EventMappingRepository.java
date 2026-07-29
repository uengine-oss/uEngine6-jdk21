package org.uengine.five.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.five.entity.EventMappingEntity;

@RepositoryRestResource(collectionResourceRel = "event-mappings", path = "event-mappings")
public interface EventMappingRepository extends JpaRepository<EventMappingEntity, Long> {

    List<EventMappingEntity> findAllByEventNameOrderByIdAsc(String eventName);

    Optional<EventMappingEntity> findByEventNameAndDefinitionIdAndTracingTagAndIsStartEvent(
            String eventName,
            String definitionId,
            String tracingTag,
            Boolean isStartEvent);
}
