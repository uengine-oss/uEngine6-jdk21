package org.uengine.hwlife.absence.dto;

/**
 * 부재 이력 조회 요청 — POST /absences/history JSON body.
 *
 * <p>조회 대상 사번은 body 가 아니라 ESB {@code header.emnb} 를 사용한다.</p>
 */
public class AbsenceHistoryRequest {
    
    private String nextKey;
    private Integer pageSize;

    public String getNextKey() {
        return nextKey;
    }

    public void setNextKey(String nextKey) {
        this.nextKey = nextKey;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
