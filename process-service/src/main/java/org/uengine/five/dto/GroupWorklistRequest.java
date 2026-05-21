package org.uengine.five.dto;

/**
 * 기관(조직) 코드·요청 타입·업무구분으로 워크리스트를 조회할 때 쿼리스트링 바인딩용.
 *
 * <p>{@code GET /worklist/search/group} 전용.
 * 처리는 {@link org.uengine.five.service.GroupWorkService#findByGroup}.</p>
 */
public class GroupWorklistRequest {

    /**
     * 기관 코드.
     * {@link #requestYn} 에 따라 요청기관 또는 진행기관 컬럼과 매칭된다.
     */
    private String groupCode;

    /**
     * 기관 검색 대상 구분 (Y/N).
     *
     * <ul>
     *   <li>{@code Y} — 요청기관: 업무를 최초로 시작한 기관
     *       ({@link org.uengine.five.entity.ProcessInstanceEntity#getInitComCd()})</li>
     *   <li>{@code N} — 진행기관: 현재 단위업무를 진행 중인 기관
     *       ({@link org.uengine.five.entity.WorklistEntity#getGroup()})</li>
     * </ul>
     */
    private String requestYn;

    /**
     * 업무구분 (프로세스 정의 ID).
     * {@link org.uengine.five.entity.WorklistEntity#getDefId()}
     */
    private String defId;

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getRequestYn() {
        return requestYn;
    }

    public void setRequestYn(String requestYn) {
        this.requestYn = requestYn;
    }

    public String getDefId() {
        return defId;
    }

    public void setDefId(String defId) {
        this.defId = defId;
    }
}
