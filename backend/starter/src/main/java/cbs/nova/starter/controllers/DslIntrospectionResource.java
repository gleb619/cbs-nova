package cbs.nova.starter.controllers;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

  @GetMapping("/helpers/search")
  @Operation(summary = "Search registered DSL helpers, processes, transactions and functions")
  @ApiResponse(responseCode = "200", description = "Matching DSL entities", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = HelperSearchResult.class))))
  public ResponseEntity<List<HelperSearchResult>> searchHelpers(
          @RequestParam(name = "name", required = false) String name,
          @RequestParam(name = "type", required = false) String type,
          @RequestParam(name = "description", required = false) String description) {
    var gm = GlobalManager.globalManager();
    List<HelperSearchResult> results = new ArrayList<>();
    gm.processNames().forEach(n -> gm.describeProcess(n)
            .ifPresent(d -> results.add(toResult(d))));
    gm.transactionNames().forEach(n -> gm.describeTransaction(n)
            .ifPresent(d -> results.add(toResult(d))));
    gm.helperNames().forEach(n -> {
      gm.describeHelper(n).ifPresent(d -> results.add(toResult(n, d)));
      gm.describeFunction(n).ifPresent(d -> results.add(toResult(d)));
    });
    var filtered = results.stream()
            .filter(r -> matches(r, name, type, description))
            .toList();
    return ResponseEntity.ok(filtered);
  }

  @GetMapping("/helpers")
  @Operation(summary = "List all registered DSL helper names")
  public ResponseEntity<NamesResponse> helpers() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.globalManager().helperNames()));
  }

  @GetMapping("/definitions")
  @Operation(summary = "List all registered DSL definitions (processes, transactions, helpers, functions)")
  @ApiResponse(responseCode = "200", description = "Aggregated DSL definitions", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DefinitionMetaDto.class))))
  public ResponseEntity<List<DefinitionMetaDto>> definitions() {
    var gm = GlobalManager.globalManager();
    List<DefinitionMetaDto> aggregate = new ArrayList<>();
    gm.processNames().forEach(n -> gm.findProcess(n)
            .ifPresent(p -> aggregate.add(
                    new DefinitionMetaDto(p.name(), "process", inputSchema(p)))));
    gm.transactionNames().forEach(n -> gm.findTransaction(n)
            .ifPresent(t -> aggregate.add(
                    new DefinitionMetaDto(t.name(), "transaction", inputSchema(t)))));
    gm.helperNames().forEach(n -> {
      gm.describeHelper(n).ifPresent(d -> aggregate.add(
              new DefinitionMetaDto(n, "helper", null)));
      gm.describeFunction(n).ifPresent(d -> aggregate.add(
              new DefinitionMetaDto(d.name(), "function", null)));
    });
    return ResponseEntity.ok(aggregate);
  }

  private static HelperSearchResult toResult(DslDescriptor descriptor) {
    return new HelperSearchResult(
            descriptor.name(),
            descriptor.type().name().toLowerCase(Locale.ROOT),
            descriptor.description(),
            typeName(descriptor.inputType()),
            typeName(descriptor.outputType()));
  }

  private static HelperSearchResult toResult(String name, ExecutableDescriptor descriptor) {
    return new HelperSearchResult(
            name,
            "helper",
            descriptor.description(),
            typeName(descriptor.inputType()),
            typeName(descriptor.outputType()));
  }

  private static boolean matches(HelperSearchResult result, String name, String type,
          String description) {
    if (name != null && !name.isBlank()
            && !result.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
      return false;
    }
    if (type != null && !type.isBlank()
            && !result.type().equalsIgnoreCase(type)) {
      return false;
    }
    if (description != null && !description.isBlank()) {
      String desc = result.description() != null ? result.description() : "";
      return desc.toLowerCase(Locale.ROOT).contains(description.toLowerCase(Locale.ROOT));
    }
    return true;
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

  public record HelperSearchResult(
          String name,
          String type,
          String description,
          String inputType,
          String outputType) {

  }

  public record DefinitionMetaDto(
          String name,
          String type,
          @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> inputSchema) {

  }
}
