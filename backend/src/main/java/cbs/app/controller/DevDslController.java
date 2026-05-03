package cbs.app.controller;

import cbs.dsl.compiler.DslScriptHost;
import cbs.dsl.compiler.DslValidator;
import cbs.dsl.compiler.ValidationError;
import cbs.dsl.runtime.DslRegistry;
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

@Slf4j
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev/dsl")
public class DevDslController {

  private final DslScriptHost scriptHost;
  private final DslValidator validator;

  @PostMapping(
      value = "/execute",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> execute(@RequestBody String scriptContent) {
    DslRegistry registry;
    try {
      registry = scriptHost.eval(scriptContent, "input");
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

    return ResponseEntity.ok(
        new DslExecuteResultDto("OK", workflows, events, transactions, massOperations));
  }

  record DslExecuteResultDto(
      String status,
      List<WorkflowSummaryDto> workflows,
      List<CodeDto> events,
      List<CodeDto> transactions,
      List<CodeDto> massOperations) {

  }

  record WorkflowSummaryDto(
      String code, String name, List<String> states, String initial, List<String> terminalStates) {

  }

  record CodeDto(String code) {

  }

  record DslExecuteErrorDto(String status, List<ValidationErrorDto> errors) {

  }

  record ValidationErrorDto(String file, String message) {

  }
}
