package org.uengine.five.lifecycle;

/** bpm-brodcast 메시지 eventType 상수 */
public final class BpmLifecycleEventType {

    /** 업무 최초 배정 (생성 시 endpoint 확정, claim) */
    public static final String TASK_ASSIGNED           = "TASK_ASSIGNED";

    /** 담당자 변경 (위임, endpoint 재배정) */
    public static final String TASK_ASSIGNMENT_CHANGED = "TASK_ASSIGNMENT_CHANGED";

    /** 업무 종료 (완료·스킵·취소·보상·위임 종료) */
    public static final String TASK_TERMINATED         = "TASK_TERMINATED";

    /** 메인 프로세스 인스턴스 전체 종료 (서브프로세스 제외) */
    public static final String PROCESS_COMPLETED       = "PROCESS_COMPLETED";

    private BpmLifecycleEventType() {}
}
