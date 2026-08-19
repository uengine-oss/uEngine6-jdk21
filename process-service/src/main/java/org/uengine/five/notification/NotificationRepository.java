package org.uengine.five.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.five.entity.NotificationEntity;

/**
 * 알림 저장소.
 *
 * <p>{@code exported = false} 는 필수다. 이 프로젝트는 Spring Data REST 의
 * RepositoryDetectionStrategy 를 커스터마이즈하지 않아 기본값(ALL)으로 동작하며,
 * 그대로 두면 {@code /notificationEntities} 가 HAL 형식으로 자동 노출되어
 * {@link NotificationController} 의 {@code /notifications} 와 의미가 갈린다.</p>
 *
 * <p>{@code isChecked} 는 {@code OracleBooleanConverter}(Boolean → NUMBER(1)) 가 걸려 있으므로
 * {@code ...IsCheckedFalse} 같은 리터럴 파생 쿼리 대신 <b>파라미터 바인딩</b>을 쓴다.
 * 파라미터로 넘겨야 AttributeConverter 가 확실히 적용된다.</p>
 */
@RepositoryRestResource(exported = false)
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findTop50ByUserIdAndIsCheckedOrderByTimeStampDesc(String userId, Boolean isChecked);

    List<NotificationEntity> findByUserIdAndUrlAndIsChecked(String userId, String url, Boolean isChecked);

    List<NotificationEntity> findByTaskIdAndIsChecked(Long taskId, Boolean isChecked);

    List<NotificationEntity> findByTaskIdAndUserIdAndIsChecked(Long taskId, String userId, Boolean isChecked);
}
