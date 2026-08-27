package cbs.nova.starter.service.introspection;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.service.introspection.mapper.DslIntrospectionMapper;
import cbs.nova.starter.service.introspection.model.DefinitionMetaDto;
import cbs.nova.starter.service.introspection.model.HelperSearchResult;
import cbs.nova.starter.service.introspection.model.NamesResponse;
import cbs.nova.starter.service.introspection.model.ProcessDetail;
import cbs.nova.starter.service.introspection.model.TransactionDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DslIntrospectionService {

  private final JsonSchemaGenerator jsonSchemaGenerator;
  private final DslIntrospectionMapper mapper;

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

  public NamesResponse helpers() {
    return new NamesResponse(GlobalManager.globalManager().helperNames());
  }

  public List<DefinitionMetaDto> definitions() {
    var gm = GlobalManager.globalManager();
    List<DefinitionMetaDto> aggregate = new ArrayList<>();
    gm.processNames().forEach(n -> gm.findProcess(n).ifPresent(p -> {
      aggregate.add(mapper.toProcessDefinitionMeta(p, inputSchema(p)));
    }));
    gm.transactionNames().forEach(n -> gm.findTransaction(n).ifPresent(t -> {
      aggregate.add(mapper.toTransactionDefinitionMeta(t, inputSchema(t)));
    }));
    gm.helperNames().forEach(n -> {
      gm.describeHelper(n).ifPresent(d -> aggregate.add(mapper.toHelperDefinitionMeta(n, d)));
      gm.describeFunction(n).ifPresent(d -> aggregate.add(mapper.toFunctionDefinitionMeta(d)));
    });
    return aggregate;
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
