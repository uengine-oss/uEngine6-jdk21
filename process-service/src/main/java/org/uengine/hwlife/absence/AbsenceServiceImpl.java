package org.uengine.hwlife.absence;


import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.absence.dto.AbsenceHistoryItem;
import org.uengine.hwlife.absence.dto.AbsenceHistoryRequest;
import org.uengine.hwlife.absence.dto.AbsenceHistoryResponse;
import org.uengine.hwlife.absence.dto.AbsenceRequest;
import org.uengine.hwlife.absence.dto.AbsenceResponse;
import org.uengine.hwlife.absence.entity.AbsenceEntity;
import org.uengine.hwlife.absence.repository.AbsenceRepository;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;

/**
 * 한화생명 융자차세대 - 부재자/대결자 설정 REST API 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class AbsenceServiceImpl implements AbsenceService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AbsenceRepository absenceRepository;

    @Autowired
    public AbsenceServiceImpl(AbsenceRepository absenceRepository) {
        this.absenceRepository = absenceRepository;
    }

    /**
     * 부재 설정/해제.
     *
     * <p>처리결과 코드는 {@code prcsRsltCntn}({@code LBM03XXXX}). 정상은 {@code LBM000000}.
     * <ul>
     *   <li>{@code LBM030001} — request body 없음</li>
     *   <li>{@code LBM030002} — header.emnb 없음</li>
     *   <li>{@code LBM030003} — agntEmnb 없음</li>
     *   <li>{@code LBM030004} — abscStarDttm 없음</li>
     *   <li>{@code LBM030005} — 부재자와 대결자가 동일</li>
     *   <li>{@code LBM030006} — 종료일시가 시작일시보다 이전</li>
     *   <li>{@code LBM030007} — 활성 부재 기간 중복</li>
     *   <li>{@code LBM030008} — fncgBpmAbstSqno 비숫자</li>
     *   <li>{@code LBM030009} — 부재 건 없음</li>
     *   <li>{@code LBM030010} — 이미 해제된 부재</li>
     * </ul>
     */
    @Override
    @RequestMapping(value = "/absences", method = RequestMethod.POST)
    @Transactional
    public AbsenceResponse executeAbsence(@RequestBody AbsenceRequest request) throws Exception {
        if (request == null) {
            return result("LBM030001");
        }

        // fncgBpmAbstSqno 가 있으면 해제, 없으면 설정
        if (request.getFncgBpmAbstSqno() != null && !request.getFncgBpmAbstSqno().trim().isEmpty()) {
            return release(request);
        }
        return register(request);
    }

    private AbsenceResponse register(AbsenceRequest request) {
        EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
        String userId = trimToNull(header != null ? header.getEmnb() : null);
        String agentUserId = trimToNull(request.getAgntEmnb());
        if (userId == null) {
            return result("LBM030002");
        }
        if (agentUserId == null) {
            return result("LBM030003");
        }
        if (request.getAbscStarDttm() == null) {
            return result("LBM030004");
        }
        if (userId.equals(agentUserId)) {
            return result("LBM030005");
        }
        if (request.getAbscEndDttm() != null && request.getAbscEndDttm().before(request.getAbscStarDttm())) {
            return result("LBM030006");
        }

        AbsenceEntity entity = new AbsenceEntity();
        entity.setUserId(userId);
        entity.setAgentUserId(agentUserId);
        entity.setAgentGroupCd(trimToNull(request.getAgntFncgOrgnCode()));
        entity.setAbscStarDttm(request.getAbscStarDttm());
        entity.setAbscEndDttm(request.getAbscEndDttm());

        if (hasOverlap(entity, null)) {
            return result("LBM030007");
        }
        absenceRepository.save(entity);
        return result("LBM000000");
    }

    private AbsenceResponse release(AbsenceRequest request) {
        Long sqno;
        try {
            sqno = Long.valueOf(request.getFncgBpmAbstSqno().trim());
        } catch (NumberFormatException e) {
            return result("LBM030008");
        }
        AbsenceEntity entity = absenceRepository.findById(sqno).orElse(null);
        if (entity == null) {
            return result("LBM030009");
        }
        if (entity.getAbscRscsDttm() != null) {
            return result("LBM030010");
        }
        entity.setAbscRscsDttm(new Date());
        absenceRepository.save(entity);
        return result("LBM000000");
    }

    @Override
    @RequestMapping(value = "/absences/history", method = RequestMethod.POST)
    @Transactional(readOnly = true)
    public AbsenceHistoryResponse searchAbsenceHistory(@RequestBody AbsenceHistoryRequest request) throws Exception {
        EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String userId = trimToNull(header != null ? header.getEmnb() : null);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "header.emnb is required");
        }
       
        Long nextKey = parseNextKey(request.getNextKey());
        int pageSize = normalizePageSize(request.getPageSize());

        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
        List<AbsenceEntity> fetched = nextKey == null
                ? absenceRepository.findHistoryFirstPage(userId, pageRequest)
                : absenceRepository.findHistoryPageAfter(userId, nextKey, pageRequest);
        AbsenceHistoryResponse response = new AbsenceHistoryResponse();
        response.setTotCont(Math.toIntExact(absenceRepository.countByUserId(userId)));
        if (fetched.size() > pageSize) {
            response.setNextKey(String.valueOf(fetched.get(pageSize).getAbseId()));
            fetched = fetched.subList(0, pageSize);
        }
        response.setAbscList(fetched.stream().map(this::toHistoryItem).toList());
        return response;
    }

    private AbsenceHistoryItem toHistoryItem(AbsenceEntity entity) {
        AbsenceHistoryItem item = new AbsenceHistoryItem();
        if (entity.getAbseId() != null) {
            item.setFncgBpmAbstSqno(String.valueOf(entity.getAbseId()));
        }
        item.setAbstEmnb(entity.getUserId());
        item.setAgntEmnb(entity.getAgentUserId());
        item.setAgntFncgOrgnCode(entity.getAgentGroupCd());
        item.setAbscStarDttm(entity.getAbscStarDttm());
        item.setAbscEndDttm(entity.getAbscEndDttm());
        item.setAbscRscsDttm(entity.getAbscRscsDttm());
        item.setAbscStupDttm(entity.getAbscCretDttm());
        return item;
    }

    private AbsenceResponse result(String prcsRsltCntn) {
        AbsenceResponse response = new AbsenceResponse();
        response.setPrcsRsltCntn(prcsRsltCntn);
        return response;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long parseNextKey(String nextKey) {
        if (nextKey == null || nextKey.trim().isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(nextKey.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("nextKey must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "nextKey must be a positive fncgBpmAbstSqno", e);
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }


    private boolean hasOverlap(AbsenceEntity target, Long excludeAbseId) {
        long excludedId = excludeAbseId == null ? -1L : excludeAbseId;
        List<AbsenceEntity> overlapping = target.getAbscEndDttm() == null
                ? absenceRepository.findOverlappingActiveWithoutEnd(
                        target.getUserId(), target.getAbscStarDttm(), excludedId)
                : absenceRepository.findOverlappingActiveWithEnd(
                        target.getUserId(), target.getAbscStarDttm(), target.getAbscEndDttm(), excludedId);
        return !overlapping.isEmpty();
    }
}
