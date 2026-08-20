package org.uengine.five.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;

/**
 * {@code inst_id} / {@code root_inst_id} 트리에서 루트 {@link ProcessInstanceEntity} 를 조회한다.
 *
 * <p>각 인스턴스 row 에 {@code init_ep}, {@code init_group_cd} 등이 있을 수 있으나,
 * 업무 검색 등에서는 {@code root_inst_id} 가 가리키는 루트 row 의 값을 쓴다.</p>
 *
 * <p>사용 예:
 * <pre>
 * Map&lt;Long, ProcessInstanceEntity&gt; cache = rootInstanceResolver.preload(worklists);
 * ProcessInstanceEntity root = rootInstanceResolver.resolve(instId, cache);
 * root.getInitEp();
 * root.getInitGroupCd();
 * root.getDefId();
 * </pre>
 */
@Component
public class RootInstanceResolver {

    private final ProcessInstanceRepository processInstanceRepository;

    public RootInstanceResolver(ProcessInstanceRepository processInstanceRepository) {
        this.processInstanceRepository = processInstanceRepository;
    }

    /** {@code root_inst_id} (없으면 {@code inst_id}). */
    public static Long rootInstIdOf(ProcessInstanceEntity instance) {
        if (instance == null) {
            return null;
        }
        return instance.getRootInstId() == null ? instance.getInstId() : instance.getRootInstId();
    }

    /** instId → root_inst_id (없으면 instId 그대로). */
    public Long resolveRootInstId(Long instId) {
        if (instId == null) {
            return null;
        }
        return processInstanceRepository.findById(instId)
                .map(RootInstanceResolver::rootInstIdOf)
                .orElse(instId);
    }

    /** 단건 — instId(서브프로세스 포함)에 대한 루트 row. */
    public ProcessInstanceEntity resolve(Long instId) {
        if (instId == null) {
            return null;
        }
        return processInstanceRepository.findById(instId)
                .map(this::resolveRootOf)
                .orElse(null);
    }

    /**
     * worklist batch 검색용 — worklist {@code instId} → 루트 {@link ProcessInstanceEntity} 캐시.
     */
    public Map<Long, ProcessInstanceEntity> preload(Collection<WorklistEntity> worklists) {
        List<ProcessInstanceEntity> instances = new ArrayList<>();
        if (worklists != null) {
            for (WorklistEntity worklist : worklists) {
                if (worklist != null && worklist.getProcessInstance() != null) {
                    instances.add(worklist.getProcessInstance());
                }
            }
        }
        return preloadInstances(instances);
    }

    /**
     * 인스턴스 batch 검색용 — {@code instId} → 루트 {@link ProcessInstanceEntity} 캐시.
     */
    public Map<Long, ProcessInstanceEntity> preloadInstances(
            Collection<ProcessInstanceEntity> instances) {
        Map<Long, ProcessInstanceEntity> byInstId = new HashMap<>();
        Map<Long, ProcessInstanceEntity> byRootInstId = new HashMap<>();
        if (instances == null) {
            return byInstId;
        }

        Set<Long> missingRootIds = new HashSet<>();
        for (ProcessInstanceEntity instance : instances) {
            Long rootInstId = rootInstIdOf(instance);
            if (rootInstId == null) {
                continue;
            }
            if (rootInstId.equals(instance.getInstId())) {
                byRootInstId.put(rootInstId, instance);
            } else {
                missingRootIds.add(rootInstId);
            }
        }
        missingRootIds.removeAll(byRootInstId.keySet());
        if (!missingRootIds.isEmpty()) {
            for (ProcessInstanceEntity root : processInstanceRepository.findAllById(missingRootIds)) {
                if (root.getInstId() != null) {
                    byRootInstId.put(root.getInstId(), root);
                }
            }
        }
        for (ProcessInstanceEntity instance : instances) {
            if (instance == null || instance.getInstId() == null) {
                continue;
            }
            Long rootInstId = rootInstIdOf(instance);
            if (rootInstId == null) {
                continue;
            }
            ProcessInstanceEntity root = byRootInstId.get(rootInstId);
            if (root != null) {
                byInstId.put(instance.getInstId(), root);
            }
        }
        return byInstId;
    }

    /** 캐시 기반 조회 — {@code preload} 후 {@code resolve(instId, cache).getXxx()} 패턴. */
    public ProcessInstanceEntity resolve(Long instId, Map<Long, ProcessInstanceEntity> cache) {
        if (instId == null) {
            return null;
        }
        if (cache != null) {
            ProcessInstanceEntity cached = cache.get(instId);
            if (cached != null) {
                return cached;
            }
        }
        ProcessInstanceEntity root = resolve(instId);
        if (root != null && cache != null) {
            cache.put(instId, root);
        }
        return root;
    }

    private ProcessInstanceEntity resolveRootOf(ProcessInstanceEntity instance) {
        Long rootInstId = rootInstIdOf(instance);
        if (rootInstId == null) {
            return null;
        }
        if (rootInstId.equals(instance.getInstId())) {
            return instance;
        }
        return processInstanceRepository.findById(rootInstId).orElse(null);
    }
}
