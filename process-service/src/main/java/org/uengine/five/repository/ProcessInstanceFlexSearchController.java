package org.uengine.five.repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.criteria.Predicate;

import org.uengine.five.entity.ProcessInstanceEntity;

/**
 * BPM_PROCINST 전체 컬럼 기반 유연 조회 엔드포인트.
 *
 * - 경로: GET /instances/search/findByAny
 * - 모든 파라미터 optional. 아무것도 안 주면 전체 조회.
 * - 하나만 줘도 동작하며, 여러 개를 동시에 주면 AND 조건으로 합산.
 * - 문자열: 완전 일치 (eq). 부분 일치가 필요하면 `XxxLike` 파라미터 사용(name/defId/defName/groups/info 지원).
 * - 날짜: `XxxFrom`/`XxxTo` 로 범위 조회 (ISO yyyy-MM-dd).
 * - 정렬/페이징은 Spring Pageable 표준 파라미터 사용: page, size, sort=필드명,desc
 *
 * JPA Specification 기반이라 Oracle/Postgres/H2 프로파일 모두에서 동작.
 */
@RestController
@RequestMapping("/instances/search")
public class ProcessInstanceFlexSearchController {

    private final ProcessInstanceRepository processInstanceRepository;

    public ProcessInstanceFlexSearchController(ProcessInstanceRepository processInstanceRepository) {
        this.processInstanceRepository = processInstanceRepository;
    }

    @GetMapping("/findByAny")
    public Page<ProcessInstanceEntity> findByAny(
            // 식별자/참조 (exact)
            @RequestParam(required = false) Long instId,
            @RequestParam(required = false) Long rootInstId,
            @RequestParam(required = false) Long mainInstId,
            @RequestParam(required = false) Long mainDefVerId,
            @RequestParam(required = false) String defVerId,
            @RequestParam(required = false) String defId,
            @RequestParam(required = false) String defPath,
            @RequestParam(required = false) String defName,
            @RequestParam(required = false) String corrKey,
            @RequestParam(required = false) String mainActTrcTag,
            @RequestParam(required = false) String mainExecScope,
            @RequestParam(required = false) String absTrcPath,
            @RequestParam(required = false) String variablesPath,

            // 상태/이름/메타 (exact)
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String info,
            @RequestParam(required = false) String name,

            // 엔드포인트(역할) 관련 (exact)
            @RequestParam(required = false) String initEp,
            @RequestParam(required = false) String initRsNm,
            @RequestParam(required = false) String prevCurrEp,
            @RequestParam(required = false) String prevCurrRsNm,
            @RequestParam(required = false) String currEp,
            @RequestParam(required = false) String currRsNm,
            @RequestParam(required = false) String groups,

            // 확장/기타 (exact)
            @RequestParam(required = false) String initComCd,
            @RequestParam(required = false) String ext1,
            @RequestParam(required = false) String ext2,
            @RequestParam(required = false) String ext3,
            @RequestParam(required = false) String ext4,
            @RequestParam(required = false) String ext5,

            // boolean 플래그
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) Boolean adhoc,
            @RequestParam(required = false) Boolean subProcess,
            @RequestParam(required = false) Boolean eventHandler,
            @RequestParam(required = false) Boolean archive,
            @RequestParam(required = false) Boolean dontReturn,

            // 부분 일치(LIKE) 편의 파라미터 - 지정 시 위의 exact보다 우선
            @RequestParam(required = false) String nameLike,
            @RequestParam(required = false) String defIdLike,
            @RequestParam(required = false) String defNameLike,
            @RequestParam(required = false) String infoLike,
            @RequestParam(required = false) String groupsLike,

            // 날짜 범위 (ISO yyyy-MM-dd 또는 yyyy-MM-dd'T'HH:mm:ss)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startedDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date finishedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date finishedDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date dueDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date modDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date modDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date defModDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date defModDateTo,

            @PageableDefault(size = 20, sort = "startedDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Specification<ProcessInstanceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ----- exact equals -----
            addEq(predicates, cb, root.get("instId"), instId);
            addEq(predicates, cb, root.get("rootInstId"), rootInstId);
            addEq(predicates, cb, root.get("mainInstId"), mainInstId);
            addEq(predicates, cb, root.get("mainDefVerId"), mainDefVerId);
            addEq(predicates, cb, root.get("defVerId"), defVerId);
            addEq(predicates, cb, root.get("defId"), defId);
            addEq(predicates, cb, root.get("defPath"), defPath);
            addEq(predicates, cb, root.get("defName"), defName);
            addEq(predicates, cb, root.get("corrKey"), corrKey);
            addEq(predicates, cb, root.get("mainActTrcTag"), mainActTrcTag);
            addEq(predicates, cb, root.get("mainExecScope"), mainExecScope);
            addEq(predicates, cb, root.get("absTrcPath"), absTrcPath);
            addEq(predicates, cb, root.get("variablesPath"), variablesPath);

            addEq(predicates, cb, root.get("status"), status);
            addEq(predicates, cb, root.get("info"), info);
            addEq(predicates, cb, root.get("name"), name);

            addEq(predicates, cb, root.get("initEp"), initEp);
            addEq(predicates, cb, root.get("initRsNm"), initRsNm);
            addEq(predicates, cb, root.get("prevCurrEp"), prevCurrEp);
            addEq(predicates, cb, root.get("prevCurrRsNm"), prevCurrRsNm);
            addEq(predicates, cb, root.get("currEp"), currEp);
            addEq(predicates, cb, root.get("currRsNm"), currRsNm);
            addEq(predicates, cb, root.get("groups"), groups);

            addEq(predicates, cb, root.get("initComCd"), initComCd);
            addEq(predicates, cb, root.get("ext1"), ext1);
            addEq(predicates, cb, root.get("ext2"), ext2);
            addEq(predicates, cb, root.get("ext3"), ext3);
            addEq(predicates, cb, root.get("ext4"), ext4);
            addEq(predicates, cb, root.get("ext5"), ext5);

            // ----- booleans -----
            addEq(predicates, cb, root.get("deleted"), deleted);
            addEq(predicates, cb, root.get("adhoc"), adhoc);
            addEq(predicates, cb, root.get("subProcess"), subProcess);
            addEq(predicates, cb, root.get("eventHandler"), eventHandler);
            addEq(predicates, cb, root.get("archive"), archive);
            addEq(predicates, cb, root.get("dontReturn"), dontReturn);

            // ----- LIKE (contains) -----
            addLike(predicates, cb, root.get("name"), nameLike);
            addLike(predicates, cb, root.get("defId"), defIdLike);
            addLike(predicates, cb, root.get("defName"), defNameLike);
            addLike(predicates, cb, root.get("info"), infoLike);
            addLike(predicates, cb, root.get("groups"), groupsLike);

            // ----- date ranges -----
            addDateRange(predicates, cb, root.get("startedDate"), startedDateFrom, startedDateTo);
            addDateRange(predicates, cb, root.get("finishedDate"), finishedDateFrom, finishedDateTo);
            addDateRange(predicates, cb, root.get("dueDate"), dueDateFrom, dueDateTo);
            addDateRange(predicates, cb, root.get("modDate"), modDateFrom, modDateTo);
            addDateRange(predicates, cb, root.get("defModDate"), defModDateFrom, defModDateTo);

            if (predicates.isEmpty()) {
                return cb.conjunction(); // 전체 조회
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return processInstanceRepository.findAll(spec, pageable);
    }

    private static <T> void addEq(List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<T> path, T value) {
        if (value == null) return;
        if (value instanceof String && ((String) value).isEmpty()) return;
        predicates.add(cb.equal(path, value));
    }

    private static void addLike(List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<String> path, String value) {
        if (value == null || value.isEmpty()) return;
        predicates.add(cb.like(path, "%" + value + "%"));
    }

    private static void addDateRange(List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<Date> path, Date from, Date to) {
        if (from != null) predicates.add(cb.greaterThanOrEqualTo(path, from));
        if (to != null) predicates.add(cb.lessThanOrEqualTo(path, to));
    }
}
