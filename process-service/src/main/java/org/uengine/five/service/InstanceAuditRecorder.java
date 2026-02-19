package org.uengine.five.service;

import java.io.Serializable;
import java.util.List;

import org.uengine.five.audit.AuditEvent;
import org.uengine.kernel.ProcessInstance;

/**
 * 인스턴스/태스크/변수 변경에 대한 감사 기록 계약.
 * DefinitionServiceUtil처럼 구현체를 교체 가능하며, 구현체에서 AuditService 등 실제 저장소를 사용한다.
 */
public interface InstanceAuditRecorder {

    /**
     * 프로세스 변수 변경 감사 기록.
     *
     * @param instance 프로세스 인스턴스 (rootInstId, instId 추출용)
     * @param varName  변수명
     * @param oldValue 이전 값 (null 가능)
     * @param newValue 새 값
     * @param taskId   작업 컨텍스트 태스크 ID (없으면 null)
     */
    void recordVariableChange(ProcessInstance instance, String varName, Serializable oldValue, Serializable newValue, String taskId);

    /**
     * 루트 인스턴스 ID 기준 감사 로그 목록 조회.
     *
     * @param rootInstId 루트 프로세스 인스턴스 ID
     * @param limit      최대 건수 (0 이하면 구현체 기본값)
     * @return 감사 이벤트 목록 (미구현 시 빈 목록)
     */
    List<AuditEvent> listByRootInstanceId(Long rootInstId, int limit);

    /**
     * 태스크 위임(재할당) 감사 기록.
     *
     * @param rootInstId              루트 인스턴스 ID
     * @param instId                  인스턴스 ID
     * @param tracingTag              트레이싱 태그
     * @param taskId                  태스크 ID
     * @param fromEndpoint            이전 수행자
     * @param toEndpoint              새 수행자 (null 가능)
     * @param delegateOnlyForWorkitem 해당 workitem만 위임 여부
     * @param actor                   수행 사용자 ID
     */
    void recordTaskDelegation(Long rootInstId, Long instId, String tracingTag, String taskId,
            String fromEndpoint, String toEndpoint, boolean delegateOnlyForWorkitem, String actor);
}
