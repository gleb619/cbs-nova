package cbs.nova.starter.converter;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.TransactionDetail;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DslIntrospectionMapper {

  @Named("typeName")
  default String typeName(Class<?> type) {
    return type == null ? null : type.getSimpleName();
  }

  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "inputSchema", ignore = true)
  ProcessDetail toProcessDetail(ProcessDslObject source);

  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "inputSchema", source = "inputSchema")
  ProcessDetail toProcessDetail(ProcessDslObject source, Map<String, Object> inputSchema);

  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "startToCloseTimeoutMs", expression = "java(source.startToCloseTimeout().toMillis())")
  @Mapping(target = "inputSchema", ignore = true)
  TransactionDetail toTransactionDetail(TransactionDslObject source);

  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "startToCloseTimeoutMs", expression = "java(source.startToCloseTimeout().toMillis())")
  @Mapping(target = "inputSchema", source = "inputSchema")
  TransactionDetail toTransactionDetail(TransactionDslObject source,
          Map<String, Object> inputSchema);

  @Mapping(target = "type", expression = "java(source.type().name().toLowerCase(java.util.Locale.ROOT))")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  HelperSearchResult toHelperSearchResult(DslDescriptor source);

  @Mapping(target = "name", source = "name")
  @Mapping(target = "type", constant = "helper")
  @Mapping(target = "inputType", expression = "java(typeName(descriptor.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(descriptor.outputType()))")
  HelperSearchResult toHelperSearchResult(String name, ExecutableDescriptor descriptor);

  @Mapping(target = "type", constant = "process")
  @Mapping(target = "version", source = "version")
  @Mapping(target = "taskQueue", source = "taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", ignore = true)
  @Mapping(target = "status", ignore = true)
  DefinitionMetaDto toProcessDefinitionMeta(ProcessDslObject source);

  @Mapping(target = "type", constant = "process")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", source = "source.taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", source = "inputSchema")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "filePath", source = "filePath")
  DefinitionMetaDto toProcessDefinitionMeta(ProcessDslObject source,
          Map<String, Object> inputSchema, DefinitionStatus status, String filePath);

  @Mapping(target = "type", constant = "transaction")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", source = "source.taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", ignore = true)
  @Mapping(target = "status", ignore = true)
  DefinitionMetaDto toTransactionDefinitionMeta(TransactionDslObject source);

  @Mapping(target = "type", constant = "transaction")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", source = "source.taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", source = "inputSchema")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "filePath", source = "filePath")
  DefinitionMetaDto toTransactionDefinitionMeta(TransactionDslObject source,
          Map<String, Object> inputSchema, DefinitionStatus status, String filePath);

  @Mapping(target = "type", constant = "function")
  @Mapping(target = "name", source = "source.name")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", ignore = true)
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", ignore = true)
  @Mapping(target = "description", source = "source.description")
  @Mapping(target = "inputSchema", ignore = true)
  @Mapping(target = "status", ignore = true)
  DefinitionMetaDto toFunctionDefinitionMeta(DslDescriptor source);

  @Mapping(target = "type", constant = "function")
  @Mapping(target = "name", source = "source.name")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", ignore = true)
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", ignore = true)
  @Mapping(target = "description", source = "source.description")
  @Mapping(target = "inputSchema", source = "inputSchema")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "filePath", source = "filePath")
  DefinitionMetaDto toFunctionDefinitionMeta(DslDescriptor source,
          Map<String, Object> inputSchema, DefinitionStatus status, String filePath);

  @Mapping(target = "type", constant = "helper")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "taskQueue", ignore = true)
  @Mapping(target = "inputType", expression = "java(typeName(descriptor.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(descriptor.outputType()))")
  @Mapping(target = "hasCompensation", ignore = true)
  @Mapping(target = "description", ignore = true)
  @Mapping(target = "inputSchema", ignore = true)
  @Mapping(target = "status", ignore = true)
  DefinitionMetaDto toHelperDefinitionMeta(String name, ExecutableDescriptor descriptor);

  @Mapping(target = "type", constant = "helper")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "taskQueue", ignore = true)
  @Mapping(target = "inputType", expression = "java(typeName(descriptor.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(descriptor.outputType()))")
  @Mapping(target = "hasCompensation", ignore = true)
  @Mapping(target = "description", ignore = true)
  @Mapping(target = "inputSchema", source = "inputSchema")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "filePath", source = "filePath")
  DefinitionMetaDto toHelperDefinitionMeta(String name, ExecutableDescriptor descriptor,
          Map<String, Object> inputSchema, DefinitionStatus status, String filePath);
}
