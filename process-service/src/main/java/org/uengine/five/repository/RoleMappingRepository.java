package org.uengine.five.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
// import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.five.entity.RoleMappingEntity;

/**
 * Created by uengine on 2017. 6. 19..
 */
// @RepositoryRestResource(collectionResourceRel = "rolemapping", path = "rolemapping")
public interface RoleMappingRepository extends JpaRepository<RoleMappingEntity, Long> {

    /** 동일 refId 로 이전 배정된 처리자(endpoint) 목록 — 재배정 제외용. */
    @Query("select distinct r.endpoint from RoleMappingEntity r "
            + "where r.refId = :refId and r.endpoint is not null and r.endpoint <> ''")
    List<String> findDistinctEndpointsByRefId(@Param("refId") String refId);
}

