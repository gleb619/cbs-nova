package cbs.nova.controller;

import cbs.nova.model.MassOperationDto;
import cbs.nova.model.MassOperationItemDto;
import cbs.nova.model.MassOperationTriggerRequest;
import cbs.nova.service.MassOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/mass-operations")
@RequiredArgsConstructor
@Tag(name = "MassOperations", description = "Mass operation execution API")
public class MassOperationController {

  private final MassOperationService massOperationService;

  @PostMapping("/trigger")
  @Operation(summary = "Trigger a mass operation")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Mass operation triggered"),
    @ApiResponse(responseCode = "400", description = "Validation error"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<MassOperationDto> trigger(
      @Valid @RequestBody MassOperationTriggerRequest request) {
    MassOperationDto result = massOperationService.trigger(request);
    return ResponseEntity.status(201).body(result);
  }

  @GetMapping
  @Operation(summary = "List all mass operation executions")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "List of executions"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<List<MassOperationDto>> findAll() {
    return ResponseEntity.ok(massOperationService.findAll());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get mass operation execution by ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "404", description = "Not found"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<MassOperationDto> findById(@PathVariable Long id) {
    return ResponseEntity.ok(massOperationService.findById(id));
  }

  @GetMapping("/code/{code}")
  @Operation(summary = "List mass operation executions by code")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "List of executions"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<List<MassOperationDto>> findByCode(@PathVariable String code) {
    return ResponseEntity.ok(massOperationService.findByCode(code));
  }

  @GetMapping("/{id}/items")
  @Operation(summary = "List items for a mass operation execution")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "404", description = "Execution not found"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<List<MassOperationItemDto>> findItems(@PathVariable Long id) {
    return ResponseEntity.ok(massOperationService.findItems(id));
  }

  @PostMapping("/{id}/retry")
  @Operation(summary = "Retry failed items for a mass operation execution")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Retry triggered"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "404", description = "Execution not found"),
    @ApiResponse(responseCode = "500", description = "Internal error")
  })
  public ResponseEntity<RetryResultDto> retryFailedItems(@PathVariable Long id) {
    int retriedCount = massOperationService.retryFailedItems(id);
    return ResponseEntity.ok(new RetryResultDto(retriedCount));
  }

  record RetryResultDto(int retriedCount) {}
}
