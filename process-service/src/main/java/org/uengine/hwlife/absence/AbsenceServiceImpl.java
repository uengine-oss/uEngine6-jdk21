package org.uengine.hwlife.absence;

import java.util.Date;
import java.util.List;

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
    public AbsenceEntity register(@RequestBody AbsenceEntity request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        AbsenceEntity entity = new AbsenceEntity();
        entity.setUserId(request.getUserId());
        entity.setUserNm(request.getUserNm());
        entity.setAgentUserId(request.getAgentUserId());
        entity.setAgentUserNm(request.getAgentUserNm());
        entity.setAbscStarDttm(request.getAbscStarDttm());
        entity.setAbscEndDttm(request.getAbscEndDttm());
        entity.setStatus(AbsenceEntity.STATUS_ACTIVE);
        entity.setCreatedDate(new Date());

        validate(entity);
        ensureNoOverlap(entity, null);

        return absenceRepository.save(entity);
    }

    @Override
    @RequestMapping(value = "/absences/user/{userId}", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public List<AbsenceEntity> findHistory(@PathVariable("userId") String userId) throws Exception {
        return absenceRepository.findByUserId(userId);
    }

    @Override
    @RequestMapping(value = "/absences/{abseId}/release", method = RequestMethod.POST)
    @Transactional
    public AbsenceEntity release(@PathVariable("abseId") Long abseId) throws Exception {
        AbsenceEntity entity = mustGet(abseId);
        if (entity.getCnceDttm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already released absence: " + abseId);
        }
        entity.setCnceDttm(new Date());
        entity.setStatus(AbsenceEntity.STATUS_TERMINATED);
        return absenceRepository.save(entity);
    }

    private AbsenceEntity mustGet(Long abseId) {
        return absenceRepository.findById(abseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Absence not found: " + abseId));
    }

    private void validate(AbsenceEntity e) {
        require(e.getUserId(), "userId");
        require(e.getUserNm(), "userNm");
        require(e.getAgentUserId(), "agentUserId");
        require(e.getAgentUserNm(), "agentUserNm");
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

    private void require(String v, String field) {
        if (v == null || v.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
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
