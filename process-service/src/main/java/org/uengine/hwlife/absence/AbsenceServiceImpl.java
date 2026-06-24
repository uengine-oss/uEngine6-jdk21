package org.uengine.hwlife.absence;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.absence.dto.AbsenceHistoryRequest;
import org.uengine.hwlife.absence.dto.AbsenceRequest;
import org.uengine.hwlife.absence.entity.AbsenceEntity;
import org.uengine.hwlife.absence.repository.AbsenceRepository;

/**
 * 한화생명 융자차세대 - 부재자/대결자 설정 REST API 구현.
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
    public AbsenceEntity process(@RequestBody AbsenceRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String bswrDvsnVal = require(request.getBswrDvsnVal(), "bswrDvsnVal");
        if (AbsenceRequest.BSWR_DVSN_RLS.equalsIgnoreCase(bswrDvsnVal)) {
            return release(request);
        }
        if (AbsenceRequest.BSWR_DVSN_REG.equalsIgnoreCase(bswrDvsnVal)) {
            return register(request);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "bswrDvsnVal must be 0 (register) or 1 (release): " + bswrDvsnVal);
    }

    private AbsenceEntity register(AbsenceRequest request) {
        AbsenceEntity entity = new AbsenceEntity();
        entity.setUserId(request.getUserId());
        entity.setUserName(request.getUserName());
        entity.setAgentUserId(request.getAgentUserId());
        entity.setAgentUserName(request.getAgentUserName());
        entity.setAgentGroupCd(request.getAgentGroupCd());
        entity.setAbscStarDttm(request.getAbscStarDttm());
        entity.setAbscEndDttm(request.getAbscEndDttm());

        validate(entity);
        ensureNoOverlap(entity, null);

        return absenceRepository.save(entity);
    }

    private AbsenceEntity release(AbsenceRequest request) {
        if (request.getAbseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abseId is required for release");
        }
        AbsenceEntity entity = mustGet(request.getAbseId());
        if (entity.getAbscCnceDttm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already released absence: " + request.getAbseId());
        }
        entity.setAbscCnceDttm(new Date());
        return absenceRepository.save(entity);
    }

    @Override
    @RequestMapping(value = "/absences/history", method = RequestMethod.POST)
    @Transactional(readOnly = true)
    public List<AbsenceEntity> findHistory(@RequestBody AbsenceHistoryRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        require(request.getUserId(), "userId");
        return absenceRepository.findByUserId(request.getUserId());
    }

    private AbsenceEntity mustGet(Long abseId) {
        return absenceRepository.findById(abseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Absence not found: " + abseId));
    }

    private void validate(AbsenceEntity e) {
        require(e.getUserId(), "userId");
        require(e.getUserName(), "userName");
        require(e.getAgentUserId(), "agentUserId");
        require(e.getAgentUserName(), "agentUserName");
        if (e.getAbscStarDttm() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abscStarDttm is required");
        }
        if (e.getUserId().equals(e.getAgentUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "userId and agentUserId must be different");
        }
        if (e.getAbscEndDttm() != null && e.getAbscEndDttm().before(e.getAbscStarDttm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "abscEndDttm must be after abscStarDttm");
        }
    }

    private String require(String v, String field) {
        if (v == null || v.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return v.trim();
    }

    private void ensureNoOverlap(AbsenceEntity target, Long excludeAbseId) {
        List<AbsenceEntity> overlapping = absenceRepository.findOverlappingActive(
                target.getUserId(),
                target.getAbscStarDttm(),
                target.getAbscEndDttm(),
                excludeAbseId == null ? -1L : excludeAbseId);
        if (!overlapping.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Overlapping active absence already exists for userId=" + target.getUserId()
                            + " (conflict id=" + overlapping.get(0).getAbseId() + ")");
        }
    }
}
