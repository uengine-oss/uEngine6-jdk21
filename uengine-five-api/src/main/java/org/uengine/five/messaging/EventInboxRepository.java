package org.uengine.five.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;

public interface EventInboxRepository extends JpaRepository<EventInbox, Long> {

    /**
     * 미처리 inbox row 를 배치 크기만큼 잠그고 가져온다. Postgres 의
     * FOR UPDATE SKIP LOCKED 로 다중 인스턴스 환경에서도 중복 없이 병렬 처리.
     *
     * <p>호출은 반드시 트랜잭션 내부에서 이루어져야 한다.
     */
    @Query(value = "SELECT * FROM BPM_EVENT_INBOX " +
                   "WHERE processed_at IS NULL " +
                   "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<EventInbox> lockUnprocessed(@Param("limit") int limit);

    /** TTL 청소용 — 일정 시점 이전의 처리완료 row 삭제. */
    @Modifying
    @Query("DELETE FROM EventInbox e WHERE e.processedAt IS NOT NULL AND e.processedAt < :olderThan")
    int deleteProcessedBefore(@Param("olderThan") Instant olderThan);

    /** (corr_key, event_name) 멱등 충돌 시 기존 row 조회용. */
    Optional<EventInbox> findFirstByCorrKeyAndEventName(String corrKey, String eventName);

    /** Spring Data REST 자동 노출 차단. */
    @Override
    @RestResource(exported = false)
    <S extends EventInbox> S save(S entity);

    @Override
    @RestResource(exported = false)
    void deleteById(Long id);

    @Override
    @RestResource(exported = false)
    void delete(EventInbox entity);
}
