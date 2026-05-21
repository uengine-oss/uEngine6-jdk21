package org.uengine.five.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.five.dto.GroupWorklistRequest;
import org.uengine.five.entity.WorklistEntity;

/**
 * 기관(그룹) 단위 업무 REST API (커스텀).
 *
 * <p>프로세스 인스턴스·워크리스트를 기관 코드 기준으로 다룬다.
 * 구현: {@link GroupWorkServiceImpl}.
 * 조회는 {@code /worklist/search/group}, 처리(변경)는 {@code /worklist/group/...}.</p>
 */
public interface GroupWorkService {

    /**
     * 기관 코드·요청/진행 구분·업무구분으로 워크리스트 페이지 조회.
     *
     * <pre>
     *   GET /worklist/search/group?groupCode=ORG001&amp;requestYn=N&amp;page=0&amp;size=20
     * </pre>
     */
    @RequestMapping(value = "/worklist/search/group", method = RequestMethod.GET)
    Page<WorklistEntity> findByGroup(
            @ModelAttribute GroupWorklistRequest req,
            Pageable pageable);

    /**
     * 해당 기관(그룹) 조건에 맞는 워크아이템 담당자를 일괄 변경.
     *
     * <pre>
     *   PUT /worklist/group/assignee
     * </pre>
     */
    @PutMapping("/worklist/group/assignee")
    void changeGroupAssignee(@RequestBody(required = false) Map<String, Object> body);
}
