package cbs.nova.starter.controllers;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dsl")
@Tag(name = "DSL Introspection", description = "Inspect registered DSL entities")
public class DslIntrospectionResource {

  private static String typeName(Class<?> type) {
    return type == null ? null : type.getSimpleName();
  }

  private static Map<String, Object> inputSchema(DslObject entity) {
    if (entity instanceof ProcessDslObject p) {
      return p.inputType() != null
              ? JsonSchemaGenerator.generateSchema(p.inputType())
              : JsonSchemaGenerator.generateSchema(p.parameters());
    }
    if (entity instanceof TransactionDslObject t) {
      return t.inputType() != null
              ? JsonSchemaGenerator.generateSchema(t.inputType())
              : JsonSchemaGenerator.generateSchema(t.parameters());
    }
    return JsonSchemaGenerator.generateSchema((Class<?>) null);
  }

  @GetMapping("/processes")
  @Operation(summary = "List all registered DSL process names")
  public ResponseEntity<NamesResponse> processes() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.globalManager().processNames()));
  }

  @GetMapping("/processes/{name}")
  @Operation(summary = "Get metadata of a single DSL process")
  public ResponseEntity<?> processDetail(@PathVariable String name) {
    return GlobalManager.globalManager()
            .findProcess(name)
            .<ResponseEntity<?>>map(
                    p -> ResponseEntity.ok(
                            new ProcessDetail(
                                    p.name(),
                                    p.version(),
                                    p.taskQueue(),
                                    typeName(p.inputType()),
                                    typeName(p.outputType()),
                                    p.compensationLogic() != null,
                                    inputSchema(p))))
            .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/transactions")
  @Operation(summary = "List all registered DSL transaction names")
  public ResponseEntity<NamesResponse> transactions() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.globalManager().transactionNames()));
  }

  @GetMapping("/transactions/{name}")
  @Operation(summary = "Get metadata of a single DSL transaction")
  public ResponseEntity<?> transactionDetail(@PathVariable String name) {
    return GlobalManager.globalManager()
            .findTransaction(name)
            .<ResponseEntity<?>>map(
                    t -> ResponseEntity.ok(
                            new TransactionDetail(
                                    t.name(),
                                    t.version(),
                                    t.taskQueue(),
                                    typeName(t.inputType()),
                                    typeName(t.outputType()),
                                    t.compensationLogic() != null,
                                    t.startToCloseTimeout().toMillis(),
                                    inputSchema(t))))
            .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/helpers")
  @Operation(summary = "List all registered DSL helper names")
  public ResponseEntity<NamesResponse> helpers() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.globalManager().helperNames()));
  }

  public record NamesResponse(List<String> names) {

  }

  public record ProcessDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
          Map<String, Object> inputSchema) {

  }

  public record TransactionDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
          long startToCloseTimeoutMs,
          Map<String, Object> inputSchema) {

  }
}
