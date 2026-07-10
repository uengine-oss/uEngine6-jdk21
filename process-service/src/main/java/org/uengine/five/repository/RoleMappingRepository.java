package org.uengine.five.repository;


import org.springframework.data.jpa.repository.JpaRepository;
//import org.metaworks.multitenancy.persistence.MultitenantRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.five.entity.RoleMappingEntity;

import java.util.Optional;

/**
 * Created by uengine on 2017. 6. 19..
 */
@RepositoryRestResource(collectionResourceRel = "rolemapping", path = "rolemapping")
public interface RoleMappingRepository extends JpaRepository<RoleMappingEntity, Long> {

    @Query("select rm from RoleMappingEntity rm " +
            "where rm.processInstance.instId = :instId " +
            "and rm.roleName = :roleName " +
            "and rm.endpoint = :endpoint " +
            "and rm.policyId = :policyId " +
            "and rm.difficulty = :difficulty " +
            "order by rm.roleMappingId desc")
    Optional<RoleMappingEntity> findLatestRuleAssignment(
            @Param("instId") Long instId,
            @Param("roleName") String roleName,
            @Param("endpoint") String endpoint,
            @Param("policyId") String policyId,
            @Param("difficulty") String difficulty);
}

