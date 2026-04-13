package cbs.nova.service;

import cbs.nova.mapper.WorkflowExecutionMapper;
import cbs.nova.model.PaginatedResponse;
import cbs.nova.model.WorkflowExecutionDto;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowExecutionService {

  private final WorkflowExecutionRepository repository;
  private final WorkflowExecutionMapper mapper;

  public PaginatedResponse<WorkflowExecutionDto> findAll(int page, int size) {
    log.debug("Finding all workflow executions: page={}, size={}", page, size);
    Page<WorkflowExecutionDto> pageResult =
        repository.findAll(PageRequest.of(page, size)).map(mapper::toDto);
    return PaginatedResponse.of(
        pageResult.getContent(),
        pageResult.getTotalElements(),
        pageResult.getNumber(),
        pageResult.getSize());
  }

  public Page<WorkflowExecutionDto> findAll(Pageable pageable) {
    log.debug("Finding all workflow executions with pageable: {}", pageable);
    return repository.findAll(pageable).map(mapper::toDto);
  }

  public WorkflowExecutionDto findById(Long id) {
    log.debug("Finding workflow execution by id: {}", id);
    return repository
        .findById(id)
        .map(mapper::toDto)
        .orElseThrow(() -> new EntityNotFoundException("WorkflowExecution", id));
  }
}
