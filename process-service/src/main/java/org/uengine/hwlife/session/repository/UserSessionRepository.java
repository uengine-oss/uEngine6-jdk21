package org.uengine.hwlife.session.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.uengine.hwlife.session.entity.UserSessionEntity;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {

    Optional<UserSessionEntity> findBySessionToken(String sessionToken);

    List<UserSessionEntity> findByUserIdAndStatus(String userId, String status);

    @Query("select s from UserSessionEntity s "
            + "where s.userId = :userId and s.result = 'SUCCESS' "
            + "order by s.loginAt desc")
    List<UserSessionEntity> findSuccessByUserIdOrderByLoginAtDesc(
            @Param("userId") String userId, Pageable pageable);
}
