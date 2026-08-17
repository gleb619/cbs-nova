package cbs.nova.starter.controllers;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DslIntrospectionHandler {

  private final JsonSchemaGenerator jsonSchemaGenerator;

  private static String typeName(Class<?> type) {
    return type == null ? null : type.getSimpleName();
  }

  private Map<String, Object> inputSchema(DslObject entity) {
    if (entity instanceof ProcessDslObject p) {
      return p.inputType() != null
              ? jsonSchemaGenerator.generateSchema(p.inputType())
              : jsonSchemaGenerator.generateSchema(p.parameters());
    }
    if (entity instanceof TransactionDslObject t) {
      return t.inputType() != null
              ? jsonSchemaGenerator.generateSchema(t.inputType())
              : jsonSchemaGenerator.generateSchema(t.parameters());
    }
    return jsonSchemaGenerator.generateSchema((Class<?>) null);
  }

  public ServerResponse processes(ServerRequest request) {
    return ServerResponse.ok()
            .body(new NamesResponse(GlobalManager.globalManager().processNames()));
  }

  public ServerResponse processDetail(ServerRequest request) {
    String name = request.pathVariable("name");
    return GlobalManager.globalManager()
            .findProcess(name)
            .<ServerResponse>map(p -> ServerResponse.ok().body(
                    new ProcessDetail(
                            p.name(),
                            p.version(),
                            p.taskQueue(),
                            typeName(p.inputType()),
                            typeName(p.outputType()),
                            p.compensationLogic() != null,
                            inputSchema(p))))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse transactions(ServerRequest request) {
    return ServerResponse.ok()
            .body(new NamesResponse(GlobalManager.globalManager().transactionNames()));
  }

  public ServerResponse transactionDetail(ServerRequest request) {
    String name = request.pathVariable("name");
    return GlobalManager.globalManager()
            .findTransaction(name)
            .<ServerResponse>map(t -> ServerResponse.ok().body(
                    new TransactionDetail(
                            t.name(),
                            t.version(),
                            t.taskQueue(),
                            typeName(t.inputType()),
                            typeName(t.outputType()),
                            t.compensationLogic() != null,
                            t.startToCloseTimeout().toMillis(),
                            inputSchema(t))))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse searchObjects(ServerRequest request) {
    String name = request.param("name").orElse(null);
    String type = request.param("type").orElse(null);
    String description = request.param("description").orElse(null);
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
    return ServerResponse.ok().body(filtered);
  }

  public ServerResponse helpers(ServerRequest request) {
    return ServerResponse.ok().body(new NamesResponse(GlobalManager.globalManager().helperNames()));
  }

  public ServerResponse definitions(ServerRequest request) {
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
    return ServerResponse.ok().body(aggregate);
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
