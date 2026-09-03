package cbs.nova.starter.service;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.HelpersResponse;
import cbs.nova.starter.model.DslIntrospectionModels.NamesResponse;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.StepDto;
import cbs.nova.starter.model.DslIntrospectionModels.TransactionDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DslIntrospectionService {

  private final JsonSchemaGenerator jsonSchemaGenerator;
  private final DslIntrospectionMapper mapper;
  private final DslDefinitionStatusResolver statusResolver;

  public NamesResponse processes() {
    return new NamesResponse(GlobalManager.globalManager().processNames());
  }

  public Optional<ProcessDetail> processDetail(String name) {
    return GlobalManager.globalManager()
            .findProcess(name)
            .map(this::toProcessDetail);
  }

  public NamesResponse transactions() {
    return new NamesResponse(GlobalManager.globalManager().transactionNames());
  }

  public Optional<TransactionDetail> transactionDetail(String name) {
    return GlobalManager.globalManager()
            .findTransaction(name)
            .map(this::toTransactionDetail);
  }

  public List<HelperSearchResult> searchObjects(String name, String type, String description) {
    var gm = GlobalManager.globalManager();
    List<HelperSearchResult> results = new ArrayList<>();
    gm.processNames().forEach(n -> gm.describeProcess(n).ifPresent(d -> results.add(toResult(d))));
    gm.transactionNames()
            .forEach(n -> gm.describeTransaction(n).ifPresent(d -> results.add(toResult(d))));
    gm.helperNames().forEach(n -> {
      gm.describeHelper(n).ifPresent(d -> results.add(toResult(n, d)));
      gm.describeFunction(n).ifPresent(d -> results.add(toResult(d)));
    });
    return results.stream()
            .filter(r -> matches(r, name, type, description))
            .toList();
  }

  public HelpersResponse helpers() {
    var gm = GlobalManager.globalManager();
    var names = gm.helperNames();
    var helpers = names.stream()
            .map(n -> toHelperCatalogEntry(n, gm.describeHelper(n)))
            .toList();
    return new HelpersResponse(names, helpers);
  }

  private HelperCatalogEntry toHelperCatalogEntry(String name,
          Optional<ExecutableDescriptor> descriptorOpt) {
    return descriptorOpt
            .map(d -> new HelperCatalogEntry(
                    name,
                    d.description(),
                    mapper.typeName(d.inputType()),
                    mapper.typeName(d.outputType()),
                    d.hasSideEffects(),
                    d.previewBehavior()))
            .orElse(new HelperCatalogEntry(name, null, null, null, false, null));
  }

  public Optional<ConstructBodyDto> constructBody(String name) {
    var gm = GlobalManager.globalManager();
    var processOpt = gm.findProcess(name);
    if (processOpt.isPresent()) {
      var p = processOpt.get();
      var code = gm.findGeneratedProcess(name).map(GeneratedClassDescriptor::executeJson)
              .orElse(null);
      var steps = p.transactionRefs() != null
              ? p.transactionRefs().stream()
                      .map(ref -> new StepDto(ref, "transaction", ref, null))
                      .toList()
              : List.<StepDto>of();
      return Optional.of(new ConstructBodyDto(p.name(), "process", code, steps));
    }
    var txOpt = gm.findTransaction(name);
    if (txOpt.isPresent()) {
      var t = txOpt.get();
      var code = gm.findGeneratedTransaction(name).map(GeneratedClassDescriptor::executeJson)
              .orElse(null);
      return Optional.of(new ConstructBodyDto(t.name(), "transaction", code, List.of()));
    }
    return Optional.empty();
  }

  public List<DefinitionMetaDto> definitions() {
    var gm = GlobalManager.globalManager();
    Set<String> allNames = new HashSet<>();
    gm.processNames().forEach(allNames::add);
    gm.transactionNames().forEach(allNames::add);
    gm.helperNames().forEach(allNames::add);
    Map<String, DefinitionStatus> statuses = statusResolver.resolveAll(allNames);

    List<DefinitionMetaDto> aggregate = new ArrayList<>();
    gm.processNames().forEach(n -> gm.findProcess(n).ifPresent(p -> {
      aggregate.add(mapper.toProcessDefinitionMeta(p, inputSchema(p), status(n, statuses),
              gm.findFilename(n).orElse(null)));
    }));
    gm.transactionNames().forEach(n -> gm.findTransaction(n).ifPresent(t -> {
      aggregate.add(mapper.toTransactionDefinitionMeta(t, inputSchema(t), status(n, statuses),
              gm.findFilename(n).orElse(null)));
    }));
    gm.helperNames().forEach(n -> {
      gm.describeHelper(n).ifPresent(d -> aggregate.add(mapper.toHelperDefinitionMeta(n, d,
              null, status(n, statuses), null)));
      gm.describeFunction(n).ifPresent(d -> aggregate.add(mapper.toFunctionDefinitionMeta(d,
              null, status(n, statuses), null)));
    });
    return aggregate;
  }

  private DefinitionStatus status(String name, Map<String, DefinitionStatus> statuses) {
    return statuses.getOrDefault(name, DefinitionStatus.PUBLISHED);
  }

  private ProcessDetail toProcessDetail(ProcessDslObject p) {
    return mapper.toProcessDetail(p, inputSchema(p));
  }

  private TransactionDetail toTransactionDetail(TransactionDslObject t) {
    return mapper.toTransactionDetail(t, inputSchema(t));
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

  private HelperSearchResult toResult(DslDescriptor descriptor) {
    return mapper.toHelperSearchResult(descriptor);
  }

  private HelperSearchResult toResult(String name, ExecutableDescriptor descriptor) {
    return mapper.toHelperSearchResult(name, descriptor);
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
}
