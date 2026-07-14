package org.uengine.hwlife.absence;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.uengine.hwlife.absence.dto.AbsenceHistoryItem;
import org.uengine.hwlife.absence.dto.AbsenceHistoryRequest;
import org.uengine.hwlife.absence.dto.AbsenceHistoryResponse;
import org.uengine.hwlife.absence.dto.AbsenceRequest;
import org.uengine.hwlife.absence.dto.AbsenceResponse;
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
    public AbsenceResponse executeAbsence(@RequestBody AbsenceRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        // fncgBpmAbstSqno 가 있으면 해제, 없으면 설정
        if (request.getFncgBpmAbstSqno() != null && !request.getFncgBpmAbstSqno().trim().isEmpty()) {
            return toResponse(release(request));
        }
        return toResponse(register(request));
    }

    private AbsenceEntity register(AbsenceRequest request) {
        AbsenceEntity entity = new AbsenceEntity();
        entity.setUserId(request.getAbscEmnb());
        entity.setAgentUserId(request.getAgntEmnb());
        entity.setAgentGroupCd(request.getAgntFncgOrgnCode());
        entity.setAbscStarDttm(toDate(request.getAbscStarDttm()));
        entity.setAbscEndDttm(toDate(request.getAbscEndDttm()));

        validate(entity);
        ensureNoOverlap(entity, null);

        return absenceRepository.save(entity);
    }

    private AbsenceEntity release(AbsenceRequest request) {
        Long sqno = parseSqno(request.getFncgBpmAbstSqno());
        AbsenceEntity entity = mustGet(sqno);
        if (entity.getAbscRscsDttm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already released absence: " + request.getFncgBpmAbstSqno());
        }
        entity.setAbscRscsDttm(new Date());
        return absenceRepository.save(entity);
    }

    @Override
    @RequestMapping(value = "/absences/history", method = RequestMethod.POST)
    @Transactional(readOnly = true)
    public AbsenceHistoryResponse searchAbsenceHistory(@RequestBody AbsenceHistoryRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        require(request.getAbscEmnb(), "abscEmnb");

        List<AbsenceEntity> all = absenceRepository.findByUserId(request.getAbscEmnb().trim());
        AbsenceHistoryResponse response = new AbsenceHistoryResponse();
        response.setTotCont(all.size());

        int pageSize = 20;
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int from = (pageNo - 1) * pageSize;
        if (from >= all.size()) {
            response.setAbscList(List.of());
            return response;
        }
        int to = Math.min(from + pageSize, all.size());
        List<AbsenceHistoryItem> page = all.subList(from, to).stream()
                .map(this::toHistoryItem)
                .toList();
        response.setAbscList(page);
        return response;
    }

    private AbsenceHistoryItem toHistoryItem(AbsenceEntity entity) {
        AbsenceHistoryItem item = new AbsenceHistoryItem();
        if (entity.getAbseId() != null) {
            item.setFncgBpmAbstSqno(String.valueOf(entity.getAbseId()));
        }
        item.setAbscEmnb(entity.getUserId());
        item.setAgntEmnb(entity.getAgentUserId());
        item.setAgntFncgOrgnCode(entity.getAgentGroupCd());
        item.setAbscStarDttm(toLocalDateTime(entity.getAbscStarDttm()));
        item.setAbscEndDttm(toLocalDateTime(entity.getAbscEndDttm()));
        item.setAbscRscsDttm(toLocalDateTime(entity.getAbscRscsDttm()));
        item.setAbscStupDttm(toLocalDateTime(entity.getAbscCretDttm()));
        return item;
    }

    private AbsenceResponse toResponse(AbsenceEntity entity) {
        AbsenceResponse response = new AbsenceResponse();
        if (entity.getAbseId() != null) {
            response.setFncgBpmAbstSqno(String.valueOf(entity.getAbseId()));
        }
        response.setAbscEmnb(entity.getUserId());
        response.setAgntEmnb(entity.getAgentUserId());
        response.setAgntFncgOrgnCode(entity.getAgentGroupCd());
        response.setAbscStarDttm(toLocalDateTime(entity.getAbscStarDttm()));
        response.setAbscEndDttm(toLocalDateTime(entity.getAbscEndDttm()));
        return response;
    }

    private AbsenceEntity mustGet(Long abseId) {
        return absenceRepository.findById(abseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Absence not found: " + abseId));
    }

    private void validate(AbsenceEntity e) {
        require(e.getUserId(), "abscEmnb");
        require(e.getAgentUserId(), "agntEmnb");
        if (e.getAbscStarDttm() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abscStarDttm is required");
        }
        if (e.getUserId().equals(e.getAgentUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "abscEmnb and agntEmnb must be different");
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

    private Long parseSqno(String sqno) {
        try {
            return Long.valueOf(sqno.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fncgBpmAbstSqno must be numeric: " + sqno);
        }
    }

    private static Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLocalDateTime(Date value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
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
