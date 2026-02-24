package org.uengine.five.entity;

import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;

import org.springframework.transaction.annotation.Transactional;

/**
 * Created by uengine on 2018. 1. 5..
 */
@Entity
@Table(name = "BPM_SERVICE")
@EntityListeners(ServiceEndpointEntityListener.class)
public class ServiceEndpointEntity {

    @Id
    String path;

    @ElementCollection
    private List<CatchEvent> events;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }


    @PrePersist
    public void trimPaths() {
        if (getPath().startsWith("/")) {
            setPath(getPath().substring(1));
        }
    }

    public List<CatchEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CatchEvent> events) {
        this.events = events;
    }

}
