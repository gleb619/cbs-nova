package cbs.app.controller;

import cbs.dsl.compiler.DslValidator;
import cbs.dsl.compiler.ValidationError;
import cbs.dsl.runtime.DslRegistry;
import cbs.dsl.script.DslScopeExtractor;
import cbs.dsl.script.EvalResult;
import cbs.dsl.script.ScriptHost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev/dsl")
public class DevDslController {

  private final DslValidator validator;
  private final ScriptHost scriptHost;

  @PostMapping(
      value = "/execute",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> execute(@RequestBody DevDslExecuteRequest request) {
    if (request.dslContent() == null || request.dslContent().isBlank()) {
      var error = new ValidationErrorDto("input", "dslContent is required and must not be blank");
      return ResponseEntity.badRequest().body(new DslExecuteErrorDto("INVALID", List.of(error)));
    }

    DslRegistry registry;
    try {
      EvalResult evalResult =
          DslScopeExtractor.evalAndExtract(scriptHost, request.dslContent(), "input.event.kts");
      if (evalResult instanceof EvalResult.Failure failure) {
        throw new IllegalStateException(failure.getMessage());
      }
      registry = ((EvalResult.Success) evalResult).getRegistry();
    } catch (IllegalStateException e) {
      log.warn("DSL script evaluation failed: {}", e.getMessage());
      var error = new ValidationErrorDto("input", e.getMessage());
      return ResponseEntity.unprocessableEntity()
          .body(new DslExecuteErrorDto("INVALID", List.of(error)));
    }

    List<ValidationError> errors = validator.validate(registry, "input");
    if (!errors.isEmpty()) {
      List<ValidationErrorDto> errorDtos = errors.stream()
          .map(e -> new ValidationErrorDto(e.getFile(), e.getMessage()))
          .toList();
      return ResponseEntity.unprocessableEntity()
          .body(new DslExecuteErrorDto("INVALID", errorDtos));
    }

    ExecutionSimulationDto executionSimulation = null;
    if (request.eventCode() != null && !request.eventCode().isBlank()) {
      executionSimulation = new ExecutionSimulationDto(
          request.eventCode(),
          request.action(),
          request.userId(),
          request.eventParameters(),
          "SIMULATED");
      log.info(
          "DSL execution simulated: eventCode={}, action={}, userId={}",
          request.eventCode(),
          request.action(),
          request.userId());
    }

    List<WorkflowSummaryDto> workflows = registry.getWorkflows().values().stream()
        .map(wf -> new WorkflowSummaryDto(
            wf.getCode(), wf.getCode(), wf.getStates(), wf.getInitial(), wf.getTerminalStates()))
        .toList();

    List<CodeDto> events = registry.getEvents().values().stream()
        .map(e -> new CodeDto(e.getCode()))
        .toList();

    List<CodeDto> transactions = registry.getTransactions().values().stream()
        .map(t -> new CodeDto(t.getCode()))
        .toList();

    List<CodeDto> massOperations = registry.getMassOperations().values().stream()
        .map(m -> new CodeDto(m.getCode()))
        .toList();

    return ResponseEntity.ok(new DslExecuteResultDto(
        "OK", workflows, events, transactions, massOperations, executionSimulation));
  }

  record DevDslExecuteRequest(
      String dslContent,
      String eventCode,
      String action,
      Map<String, Object> eventParameters,
      String userId) {}

  record DslExecuteResultDto(
      String status,
      List<WorkflowSummaryDto> workflows,
      List<CodeDto> events,
      List<CodeDto> transactions,
      List<CodeDto> massOperations,
      ExecutionSimulationDto executionSimulation) {}

  record WorkflowSummaryDto(
      String code, String name, List<String> states, String initial, List<String> terminalStates) {}

  record CodeDto(String code) {}

  record ExecutionSimulationDto(
      String eventCode,
      String action,
      String userId,
      Map<String, Object> parameters,
      String status) {}

  record DslExecuteErrorDto(String status, List<ValidationErrorDto> errors) {}

  record ValidationErrorDto(String file, String message) {}
}
