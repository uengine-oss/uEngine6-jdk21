package org.uengine.five.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.five.dto.ApprovalWorklistRequest;
import org.uengine.five.entity.WorklistEntity;

/**
 * 나의 결재함(My Approval) REST API (커스텀).
 *
 * <p>Spring Data REST 는 @Query 기반 커스텀 메서드를 자동 노출하지 않으므로 직접 노출한다.
 * 구현: {@link ApprovalWorklistServiceImpl}.</p>
 */
public interface ApprovalWorklistService {

    /**
     * 나의 결재함 동적 검색.
     *
     * <p>결재함 정의: {@code approvalType} 이 채워진 워크아이템.</p>
     *
     * <pre>
     *   GET /worklist/search/findMyApproval?endpoint=...&amp;page=0&amp;size=20
     * </pre>
     */
    @RequestMapping(value = "/worklist/search/findMyApproval", method = RequestMethod.GET)
    Page<WorklistEntity> findMyApproval(
            @ModelAttribute ApprovalWorklistRequest req,
            Pageable pageable);
}
