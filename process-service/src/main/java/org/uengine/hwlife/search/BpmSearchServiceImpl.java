package org.uengine.hwlife.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;

/**
 * BPM 통합 검색 REST API 구현. Repository 연동은 추후 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class BpmSearchServiceImpl implements BpmSearchService {

  @Override
  @Transactional(readOnly = true)
  public Page<WorklistEntity> searchMyTodo(
      @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchMyTodo");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProcessInstanceEntity> searchMyProgress(
      @PageableDefault(size = 20, sort = "startedDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchMyProgress");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProcessInstanceEntity> searchOrgRunning(
      @PageableDefault(size = 20, sort = "startedDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchOrgRunning");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProcessInstanceEntity> searchOrgCompleted(
      @PageableDefault(size = 20, sort = "finishedDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchOrgCompleted");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<WorklistEntity> searchOrgBatch(
      @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
    throw notImplemented("searchOrgBatch");
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
