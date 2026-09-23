package cbs.nova.starter.service;

import static cbs.nova.dsl.utils.ExplainReports.explainReportSchema;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.explain.DefaultExplainLogic;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.jsonschema.JsonSchemaGenerator;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.SignalDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaMode;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.HelpersResponse;
import cbs.nova.starter.model.DslIntrospectionModels.LogicInfoDto;
import cbs.nova.starter.model.DslIntrospectionModels.LogicStatus;
import cbs.nova.starter.model.DslIntrospectionModels.NamesResponse;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectStructureDto;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.StepDto;
import cbs.nova.starter.model.DslIntrospectionModels.StructureFieldDto;
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
import java.util.function.Function;

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
                    mapper.typeName(d.outputType())
            ))
            .orElse(new HelperCatalogEntry(name, null, null, null));
  }

  public Optional<ConstructBodyDto> constructBody(String name) {
    var gm = GlobalManager.globalManager();
    var processOpt = gm.findProcess(name);
    if (processOpt.isPresent()) {
      var p = processOpt.get();
      var code = gm.findGeneratedProcess(name).map(GeneratedClassDescriptor::executeJson)
              .orElse(null);
      var steps = List.<StepDto>of();
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

  public Optional<ConstructSchemaDto> constructSchema(String name, ConstructSchemaMode mode) {
    if (mode == ConstructSchemaMode.EXPLAIN) {
      return explainSchema(name);
    }
    return previewSchema(name);
  }

  private Optional<ConstructSchemaDto> previewSchema(String name) {
    var gm = GlobalManager.globalManager();
    return gm.findProcess(name).map(this::toSchemaDto)
            .or(() -> gm.findTransaction(name).map(this::toSchemaDto))
            .or(() -> gm.describeHelper(name).map(d -> toSchemaDto(name, d)))
            .or(() -> gm.describeFunction(name).map(this::toSchemaDto));
  }

  private Optional<ConstructSchemaDto> explainSchema(String name) {
    return previewSchema(name).map(dto -> new ConstructSchemaDto(
            dto.name(),
            dto.type(),
            dto.inputType(),
            ExplainReport.class.getSimpleName(),
            dto.description(),
            dto.inputSchema(),
            explainReportSchema()));
  }

  public Optional<ObjectStructureDto> objectStructure(String name) {
    var gm = GlobalManager.globalManager();
    return gm.findProcess(name).map(this::processStructure)
            .or(() -> gm.findTransaction(name).map(this::transactionStructure))
            .or(() -> gm.describeHelper(name)
                    .map(d -> executableStructure(name, "helper", d, helperLogic())))
            .or(() -> gm.findFunction(name).map(this::functionStructure));
  }

  private ObjectStructureDto processStructure(ProcessDslObject process) {
    List<StructureFieldDto> fields = new ArrayList<>();
    fields.add(scalar("name", process.name(), "Unique DSL process identifier"));
    fields.add(scalar("version", process.version(), "Schema/version tag, default v1"));
    fields.add(scalar("taskQueue", process.taskQueue(),
            "Temporal task queue this workflow polls"));
    fields.add(classField("inputType", process.inputType(),
            "Java type of the workflow input payload; fields of the model are listed"
                    + " under 'input.*' when a schema is generated"));
    fields.add(classField("outputType", process.outputType(),
            "Java type returned by the process"));
    fields.add(scalar("hasCompensation", process.compensationLogic() != null,
            "Whether a compensation handler is registered for rollback"));
    fields.add(scalar("description", process.description(),
            "Markdown description of the process"));
    fields.addAll(signalRows(process.signals()));
    fields.addAll(parameterRows("parameters", process.parameters(),
            "Named workflow parameters when MapInput/MapOutput style is used"
                    + " instead of typed input"));
    return new ObjectStructureDto(process.name(), "process", fields,
            builderLogic(process.previewLogic(), process.executeLogic(), process.explainLogic()));
  }

  private ObjectStructureDto transactionStructure(TransactionDslObject transaction) {
    List<StructureFieldDto> fields = new ArrayList<>();
    fields.add(scalar("name", transaction.name(), "Unique DSL transaction identifier"));
    fields.add(scalar("version", transaction.version(), "Schema/version tag, default v1"));
    fields.add(scalar("taskQueue", transaction.taskQueue(),
            "Temporal task queue this activity polls"));
    fields.add(classField("inputType", transaction.inputType(),
            "Java type of the activity input payload; fields of the model are listed"
                    + " under 'input.*' when a schema is generated"));
    fields.add(classField("outputType", transaction.outputType(),
            "Java type returned by the transaction"));
    fields.add(scalar("hasCompensation", transaction.compensationLogic() != null,
            "Whether a compensation handler is registered for rollback"));
    fields.add(scalar("description", transaction.description(),
            "Markdown description of the transaction"));
    fields.add(scalar("startToCloseTimeout", transaction.startToCloseTimeout(),
            "Maximum time the activity may run before Temporal marks it timed out"));
    fields.add(scalar("heartbeatTimeout", transaction.heartbeatTimeout(),
            "Maximum time between heartbeats before Temporal considers the activity"
                    + " stuck; null disables heartbeat monitoring"));
    fields.addAll(parameterRows("parameters", transaction.parameters(),
            "Named activity parameters when MapInput/MapOutput style is used"
                    + " instead of typed input"));
    return new ObjectStructureDto(transaction.name(), "transaction", fields,
            builderLogic(transaction.previewLogic(), transaction.executeLogic(),
                    transaction.explainLogic()));
  }

  private ObjectStructureDto executableStructure(String name, String type,
          ExecutableDescriptor descriptor, List<LogicInfoDto> logic) {
    return executableStructure(name, type, descriptor.description(),
            descriptor.inputType(), descriptor.outputType(),
        descriptor.parameters(), logic);
  }

  private ObjectStructureDto functionStructure(FunctionDslObject function) {
    var descriptor = function.descriptor();
    return executableStructure(function.name(), "function", descriptor.description(),
            descriptor.inputType(), descriptor.outputType(),
        descriptor.parameters(),
            builderLogic(function.previewLogic(), function.executeLogic(),
                    function.explainLogic()));
  }

  private ObjectStructureDto executableStructure(String name, String type, String description,
          Class<?> inputType, Class<?> outputType,
      List<ParameterDescriptor> parameters, List<LogicInfoDto> logic) {
    List<StructureFieldDto> fields = new ArrayList<>();
    fields.add(scalar("name", name, "Unique DSL " + type + " identifier"));
    fields.add(scalar("description", description,
            "Human-readable description of the " + type));
    fields.add(classField("inputType", inputType,
            "Input payload type the " + type + " accepts"));
    fields.add(classField("outputType", outputType,
            "Return type of the " + type));
    fields.addAll(parameterRows("parameters", parameters,
            "Named parameters the " + type + " accepts"));
    return new ObjectStructureDto(name, type, fields, logic);
  }

  private static List<LogicInfoDto> builderLogic(
          Function<?, ?> previewLogic, Function<?, ?> executeLogic,
          Function<?, ?> explainLogic) {
    return List.of(
            new LogicInfoDto("execute", LogicStatus.CONFIGURED, true,
                    "User-defined execute logic; required for the object to run"),
            logicEntry("preview", previewLogic != executeLogic,
                    "No preview logic configured; falls back to execute logic"),
            logicEntry("explain", !(explainLogic instanceof DefaultExplainLogic<?>),
                    "No explain logic configured; falls back to a descriptor-based report"));
  }

  private static List<LogicInfoDto> helperLogic() {
    return List.of(
            new LogicInfoDto("execute", LogicStatus.CONFIGURED, true,
                    "Implemented by the helper class; required for the helper to run"),
            logicEntry("preview", false,
                    "Delegates to execute logic unless the helper overrides preview()"),
            logicEntry("explain", false,
                    "Composes a descriptor-based report unless the helper overrides explain()"));
  }

  private static LogicInfoDto logicEntry(String kind, boolean configured, String fallback) {
    return new LogicInfoDto(
            kind,
            configured ? LogicStatus.CONFIGURED : LogicStatus.DEFAULT,
            false,
            configured ? "User-defined " + kind + " logic" : fallback);
  }

  private List<StructureFieldDto> signalRows(List<SignalDescriptor> signals) {
    List<StructureFieldDto> rows = new ArrayList<>();
    rows.add(scalar("signals", signals.size(),
            "Signals the workflow can receive mid-flight, name + payload type"));
    for (int i = 0; i < signals.size(); i++) {
      SignalDescriptor signal = signals.get(i);
      rows.add(scalar("signals[" + i + "].name", signal.name(), "Signal name"));
      rows.add(classField("signals[" + i + "].payloadType", signal.payloadType(),
              "Payload type the signal delivers"));
    }
    return rows;
  }

  private List<StructureFieldDto> parameterRows(String path,
          List<ParameterDescriptor> parameters, String description) {
    List<StructureFieldDto> rows = new ArrayList<>();
    rows.add(scalar(path, parameters.size(), description));
    for (int i = 0; i < parameters.size(); i++) {
      ParameterDescriptor parameter = parameters.get(i);
      rows.add(scalar(path + "[" + i + "].name", parameter.name(), "Parameter name"));
      rows.add(scalar(path + "[" + i + "].type", parameter.type(), "Parameter kind"));
      rows.add(classField(path + "[" + i + "].objectType", parameter.objectType(),
              "Java type carried by the parameter when the kind is OBJECT"));
    }
    return rows;
  }

  private static StructureFieldDto scalar(String path, Object value, String description) {
    return new StructureFieldDto(
            path,
            value == null ? null : String.valueOf(value),
            value == null ? null : value.getClass().getSimpleName(),
            description);
  }

  private StructureFieldDto classField(String path, Class<?> type, String description) {
    return new StructureFieldDto(path, mapper.typeName(type), "class", description);
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
      gm.describeHelper(n)
              .ifPresent(d -> aggregate.add(mapper.toHelperDefinitionMeta(n, d,
                      null, status(n, statuses), gm.findFilename(n).orElse(null))));
      gm.describeFunction(n)
              .ifPresent(d -> aggregate.add(mapper.toFunctionDefinitionMeta(d,
                      null, status(n, statuses), gm.findFilename(n).orElse(null))));
    });
    return aggregate;
  }

  private ConstructSchemaDto toSchemaDto(ProcessDslObject process) {
    var descriptor = process.descriptor();
    return toSchemaDto(
            descriptor.name(),
            descriptor.type().name().toLowerCase(Locale.ROOT),
            descriptor.description(),
            descriptor.inputType(),
            descriptor.outputType(),
            descriptor.parameters());
  }

  private ConstructSchemaDto toSchemaDto(TransactionDslObject transaction) {
    var descriptor = transaction.describe();
    return toSchemaDto(
            descriptor.name(),
            descriptor.type().name().toLowerCase(Locale.ROOT),
            descriptor.description(),
            descriptor.inputType(),
            descriptor.outputType(),
            descriptor.parameters());
  }

  private ConstructSchemaDto toSchemaDto(String name, ExecutableDescriptor descriptor) {
    return toSchemaDto(
            name,
            "helper",
            descriptor.description(),
            descriptor.inputType(),
            descriptor.outputType(),
            descriptor.parameters());
  }

  private ConstructSchemaDto toSchemaDto(DslDescriptor descriptor) {
    return toSchemaDto(
            descriptor.name(),
            descriptor.type().name().toLowerCase(Locale.ROOT),
            descriptor.description(),
            descriptor.inputType(),
            descriptor.outputType(),
            descriptor.parameters());
  }

  private ConstructSchemaDto toSchemaDto(String name, String type, String description,
          Class<?> inputType, Class<?> outputType, List<ParameterDescriptor> parameters) {
    Map<String, Object> inputSchema = schemaForInput(inputType, parameters);
    Map<String, Object> outputSchema = jsonSchemaGenerator.generateSchema(outputType);
    return new ConstructSchemaDto(
            name,
            type,
            mapper.typeName(inputType),
            mapper.typeName(outputType),
            description,
            inputSchema,
            outputSchema);
  }

  private Map<String, Object> schemaForInput(Class<?> type, List<ParameterDescriptor> parameters) {
    return type != null && type != Void.class
            ? jsonSchemaGenerator.generateSchema(type)
            : jsonSchemaGenerator.generateSchema(parameters);
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
      return schemaForInput(p.inputType(), p.parameters());
    }
    if (entity instanceof TransactionDslObject t) {
      return schemaForInput(t.inputType(), t.parameters());
    }
    return schemaForInput(null, null);
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
