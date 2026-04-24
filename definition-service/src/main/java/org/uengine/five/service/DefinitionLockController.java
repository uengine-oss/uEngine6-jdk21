package org.uengine.five.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 동시 수정 방지 Lock REST 엔드포인트.
 * DefinitionServiceImpl 의 catch-all 라우트(/definition/**, /definition/{defPath:.+})
 * 와 경로 매칭 충돌을 피하기 위해 별도 컨트롤러로 분리.
 */
@RestController
@RequestMapping("/definition/lock")
public class DefinitionLockController {

    @Autowired
    private DefinitionLockService definitionLockService;

    /** path 쿼리 파라미터 지원: 슬래시 포함 id (e.g. default/foo.bpmn) */
    @GetMapping(produces = "application/json;charset=UTF-8", params = "path")
    public DefinitionLockDto getLockByPath(@RequestParam("path") String path) {
        return definitionLockService.getLock(path).orElse(null);
    }

    @GetMapping(value = "/{id:.+}", produces = "application/json;charset=UTF-8")
    public DefinitionLockDto getLock(@PathVariable("id") String id) {
        return definitionLockService.getLock(id).orElse(null);
    }

    @PutMapping(consumes = "application/json", produces = "application/json;charset=UTF-8")
    public DefinitionLockDto putLock(@RequestBody DefinitionLockDto body) {
        String userId = body.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user_id is required");
        }
        String resourceId = body.getId();
        if (resourceId == null || resourceId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        return definitionLockService.putLock(resourceId, userId);
    }

    @DeleteMapping(params = "path")
    public ResponseEntity<Void> deleteLockByPath(@RequestParam("path") String path) {
        definitionLockService.deleteLock(path);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id:.+}")
    public ResponseEntity<Void> deleteLock(@PathVariable("id") String id) {
        definitionLockService.deleteLock(id);
        return ResponseEntity.noContent().build();
    }
}
