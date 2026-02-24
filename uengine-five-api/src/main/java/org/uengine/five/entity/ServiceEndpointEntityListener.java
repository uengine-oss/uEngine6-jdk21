package org.uengine.five.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PreRemove;
import jakarta.transaction.Transactional;

public class ServiceEndpointEntityListener {

    @PersistenceContext
    private EntityManager entityManager;

    @PreRemove
    @Transactional
    public void preRemove(ServiceEndpointEntity serviceEndpointEntity) {
        // CatchEvent 엔티티를 삭제합니다.
        serviceEndpointEntity.getEvents().clear();
        entityManager.flush();
    }
}