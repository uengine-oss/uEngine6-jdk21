package org.uengine.five.lifecycle;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.esbclient.dto.EsbCodes;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

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

    /** 업무 최초 배정 (생성 시 endpoint 확정, claim) */
    public static final String TASK_ASSIGNED           = "TASK_ASSIGNED";

    /** 담당자 변경 (위임, endpoint 재배정) */
    public static final String TASK_ASSIGNMENT_CHANGED = "TASK_ASSIGNMENT_CHANGED";

    /** 업무 종료 (완료·스킵·취소·보상·위임 종료) */
    public static final String TASK_TERMINATED         = "TASK_TERMINATED";

    /** 메인 프로세스 인스턴스 전체 종료 (서브프로세스 제외) */
    public static final String PROCESS_COMPLETED       = "PROCESS_COMPLETED";


    private String fncgBpmTaskTrcgNm; // BPM 추적 태그
    private String uworNm;
    private String hndrEmnb; // 담당자 사번
    private String hndrOrgnCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date uworStarDttm;
    private String fncgBpmUworSttsCntn; // 현재 진행중인 단위업무(WORKITME) 상태 
    private String fncgBpmTaskLstId;
    private String fncgBpmPcesIntcId;

    private String loanPcesMgmtNo; // 대출프로세스관리번호 
    private String prgsSttsNm; // 인스턴스 상태 
    private String apvlYn; // 결재 유형의 업무 여부
    private String imgeScanYn; // 이미지 스캔 여부 


    public String getUworNm() {
        return uworNm;
    }

    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
    }

    public String getHndrOrgnCode() {
        return hndrOrgnCode;
    }

    public void setHndrOrgnCode(String hndrOrgnCode) {
        this.hndrOrgnCode = hndrOrgnCode;
    }

    public String getFncgBpmTaskLstId() {
        return fncgBpmTaskLstId;
    }

    public void setFncgBpmTaskLstId(String fncgBpmTaskLstId) {
        this.fncgBpmTaskLstId = fncgBpmTaskLstId;
    }

    public String getFncgBpmPcesIntcId() {
        return fncgBpmPcesIntcId;
    }

    public void setFncgBpmPcesIntcId(String fncgBpmPcesIntcId) {
        this.fncgBpmPcesIntcId = fncgBpmPcesIntcId;
    }
     
    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }

    public String getFncgBpmUworSttsCntn() {
        return fncgBpmUworSttsCntn;
    }

    public void setFncgBpmUworSttsCntn(String fncgBpmUworSttsCntn) {
        this.fncgBpmUworSttsCntn = fncgBpmUworSttsCntn;
    }

    public String getPrgsSttsNm() {
        return prgsSttsNm;
    }

    public void setPrgsSttsNm(String prgsSttsNm) {
        this.prgsSttsNm = prgsSttsNm;
    }
    
    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getApvlYn() {
        return apvlYn;
    }

    public void setApvlYn(String apvlYn) {
        this.apvlYn = apvlYn;
    }

    public String getImgeScanYn() {
        return imgeScanYn;
    }

    public void setImgeScanYn(String imgeScanYn) {
        this.imgeScanYn = imgeScanYn;
    }

}
