package cbs.nova.controller;

import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Business event execution API")
public class EventController {

  private final EventService eventService;

  @PostMapping("/execute")
  @Operation(summary = "Execute a business event")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Event executed successfully"),
    @ApiResponse(responseCode = "400", description = "Bad request"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "404", description = "Workflow or event definition not found"),
    @ApiResponse(
        responseCode = "422",
        description = "Validation error or context evaluation failed"),
    @ApiResponse(responseCode = "500", description = "Internal execution error")
  })
  public ResponseEntity<EventExecutionResponse> execute(
      @Valid @RequestBody EventExecutionRequest request) {
    log.info(
        "Executing event: workflow={}, event={}, by={}",
        request.workflowCode(),
        request.eventCode(),
        request.performedBy());
    EventExecutionResponse response = eventService.execute(request);
    return ResponseEntity.ok(response);
  }
}
