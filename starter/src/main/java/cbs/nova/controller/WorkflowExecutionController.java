package cbs.nova.controller;

import cbs.nova.model.PaginatedResponse;
import cbs.nova.model.WorkflowExecutionDto;
import cbs.nova.service.WorkflowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
@Tag(name = "Executions", description = "Workflow execution list and detail API")
public class WorkflowExecutionController {

  private final WorkflowExecutionService workflowExecutionService;

  @GetMapping
  @Operation(summary = "List all workflow executions")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description =
              "List of executions with pagination headers (X-Total-Count, X-Page-Number, X-Page-Size, X-Total-Pages)"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<List<WorkflowExecutionDto>> findAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PaginatedResponse<WorkflowExecutionDto> result = workflowExecutionService.findAll(page, size);
    return ResponseEntity.ok()
        .header("X-Total-Count", String.valueOf(result.totalElements()))
        .header("X-Page-Number", String.valueOf(result.pageNumber()))
        .header("X-Page-Size", String.valueOf(result.pageSize()))
        .header("X-Total-Pages", String.valueOf(result.totalPages()))
        .body(result.content());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get workflow execution by ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Execution found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "404", description = "Execution not found"),
      @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<WorkflowExecutionDto> findById(@PathVariable Long id) {
    return ResponseEntity.ok(workflowExecutionService.findById(id));
  }
}
