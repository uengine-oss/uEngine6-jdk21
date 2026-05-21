package org.uengine.five.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.dto.ApprovalWorklistRequest;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;

/**
 * 나의 결재함 REST API 구현 (커스텀).
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class ApprovalWorklistServiceImpl implements ApprovalWorklistService {

    private final WorklistRepository worklistRepository;

    public ApprovalWorklistServiceImpl(WorklistRepository worklistRepository) {
        this.worklistRepository = worklistRepository;
    }

    @Override
    @RequestMapping(value = "/worklist/search/findMyApproval", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public Page<WorklistEntity> findMyApproval(
            @ModelAttribute ApprovalWorklistRequest req,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        if (req == null) {
            req = new ApprovalWorklistRequest();
        }
        return worklistRepository.findMyApproval(
                req.getEndpoint(),
                req.getDefId(),
                req.getActivityId(),
                req.getStatus(),
                req.getStartDate(),
                req.getEndDate(),
                pageable);
    }
}
