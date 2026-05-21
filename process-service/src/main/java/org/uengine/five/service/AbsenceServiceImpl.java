package org.uengine.five.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.AbsenceCreateRequest;
import org.uengine.five.dto.AbsenceResponse;
import org.uengine.five.entity.AbsenceEntity;
import org.uengine.five.repository.AbsenceRepository;

/**
 * 부재자/대결자 설정 REST API 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class AbsenceServiceImpl implements AbsenceService {

    private final AbsenceRepository absenceRepository;

    @Autowired
    public AbsenceServiceImpl(AbsenceRepository absenceRepository) {
        this.absenceRepository = absenceRepository;
    }

    @Override
    @RequestMapping(value = "/absences", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AbsenceResponse register(@RequestBody AbsenceCreateRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        AbsenceEntity entity = new AbsenceEntity();
        entity.setUserId(request.getUserId());
        entity.setUserName(request.getUserName());
        entity.setAgentUserId(request.getAgentUserId());
        entity.setAgentUserNm(request.getAgentUserNm());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setStatus(AbsenceEntity.STATUS_ACTIVE);
        entity.setCreatedDate(new Date());

        validate(entity);
        ensureNoOverlap(entity, -1L);

        AbsenceEntity saved = absenceRepository.save(entity);
        return AbsenceResponse.from(saved);
    }

    @Override
    @RequestMapping(value = "/absences/user/{userId}", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public List<AbsenceResponse> findHistory(@PathVariable("userId") String userId) throws Exception {
        return absenceRepository.findByUserId(userId).stream()
                .map(AbsenceResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @RequestMapping(value = "/absences/{id}/release", method = RequestMethod.POST)
    @Transactional
    public AbsenceResponse release(@PathVariable("id") Long absenceId) throws Exception {
        AbsenceEntity entity = mustGet(absenceId);
        if (entity.getTerminationDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already released absence: " + absenceId);
        }
        entity.setTerminationDate(new Date());
        entity.setStatus(AbsenceEntity.STATUS_TERMINATED);
        return AbsenceResponse.from(absenceRepository.save(entity));
    }

    private AbsenceEntity mustGet(Long absenceId) {
        return absenceRepository.findById(absenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Absence not found: " + absenceId));
    }

    private void validate(AbsenceEntity e) {
        require(e.getUserId(), "userId");
        require(e.getUserName(), "userName");
        require(e.getAgentUserId(), "agentUserId");
        require(e.getAgentUserNm(), "agentUserNm");
        if (e.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required");
        }
        if (e.getUserId().equals(e.getAgentUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "userId and agentUserId must be different");
        }
        if (e.getEndDate() != null && e.getEndDate().before(e.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endDate must be after startDate");
        }
    }

    private void require(String v, String field) {
        if (v == null || v.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    private void ensureNoOverlap(AbsenceEntity target, Long excludeId) {
        List<AbsenceEntity> overlapping = absenceRepository.findOverlappingActive(
                target.getUserId(),
                target.getStartDate(),
                target.getEndDate(),
                excludeId == null ? -1L : excludeId);
        if (!overlapping.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Overlapping active absence already exists for userId=" + target.getUserId()
                            + " (conflict id=" + overlapping.get(0).getAbsenceId() + ")");
        }
    }
}
