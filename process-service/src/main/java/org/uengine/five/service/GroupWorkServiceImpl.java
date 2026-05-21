package org.uengine.five.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.GroupWorklistRequest;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;

/**
 * 기관(그룹) 단위 업무 REST API 구현 (커스텀).
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class GroupWorkServiceImpl implements GroupWorkService {

    private final WorklistRepository worklistRepository;

    public GroupWorkServiceImpl(WorklistRepository worklistRepository) {
        this.worklistRepository = worklistRepository;
    }

    @Override
    @RequestMapping(value = "/worklist/search/group", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public Page<WorklistEntity> findByGroup(
            @ModelAttribute GroupWorklistRequest req,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        }
        return worklistRepository.findByGroupCode(
                req.getGroupCode(),
                req.getRequestYn(),
                req.getDefId(),
                pageable);
    }

    @Override
    @PutMapping("/worklist/group/assignee")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changeGroupAssignee(@RequestBody(required = false) Map<String, Object> body) {
        // TODO: 전용 Request/Response DTO 확정 후 구현 — 대상 조회, 권한 검증, endpoint/resName 갱신, BPM 연동
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }
}
