package org.uengine.five.lifecycle;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.esbclient.dto.EsbCodes;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

/**
 * BPM 업무/프로세스 생명주기 이벤트 메시지 객체.
 * <p>
 * eventType 별 의미:
 * <ul>
 *   <li>{@link #TASK_ASSIGNED}          - 업무 최초 배정</li>
 *   <li>{@link #TASK_ASSIGNMENT_CHANGED} - 담당자 변경 (위임·재배정)</li>
 *   <li>{@link #TASK_TERMINATED}        - 업무 종료 (완료·스킵·취소 등)</li>
 *   <li>{@link #PROCESS_COMPLETED}      - 메인 프로세스 인스턴스 전체 종료</li>
 * </ul>
 */
public class BpmLifecycleEventRequest {
    
    private String dvsnVal;
    private List<BpmLifecycleEventRequestItem> bswrList;

    public String getDvsnVal() {
        return dvsnVal;
    }
    public void setDvsnVal(String dvsnVal) {
        this.dvsnVal = dvsnVal;
    }
    public List<BpmLifecycleEventRequestItem> getBswrList() {
        return bswrList;
    }
    public void setBswrList(List<BpmLifecycleEventRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
