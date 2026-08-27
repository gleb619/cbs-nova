package cbs.nova.starter.services.introspection.mapper;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.services.introspection.model.DefinitionMetaDto;
import cbs.nova.starter.services.introspection.model.HelperSearchResult;
import cbs.nova.starter.services.introspection.model.ProcessDetail;
import cbs.nova.starter.services.introspection.model.TransactionDetail;
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
  ProcessDetail toProcessDetail(ProcessDslObject source, java.util.Map<String, Object> inputSchema);

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
          java.util.Map<String, Object> inputSchema);

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
  DefinitionMetaDto toProcessDefinitionMeta(ProcessDslObject source);

  @Mapping(target = "type", constant = "process")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", source = "source.taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", source = "inputSchema")
  DefinitionMetaDto toProcessDefinitionMeta(ProcessDslObject source,
          java.util.Map<String, Object> inputSchema);

  @Mapping(target = "type", constant = "transaction")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", source = "source.taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", ignore = true)
  DefinitionMetaDto toTransactionDefinitionMeta(TransactionDslObject source);

  @Mapping(target = "type", constant = "transaction")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", source = "source.taskQueue")
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", expression = "java(source.compensationLogic() != null)")
  @Mapping(target = "description", expression = "java(source.describe().description())")
  @Mapping(target = "inputSchema", source = "inputSchema")
  DefinitionMetaDto toTransactionDefinitionMeta(TransactionDslObject source,
          java.util.Map<String, Object> inputSchema);

  @Mapping(target = "type", constant = "function")
  @Mapping(target = "name", source = "source.name")
  @Mapping(target = "version", source = "source.version")
  @Mapping(target = "taskQueue", ignore = true)
  @Mapping(target = "inputType", expression = "java(typeName(source.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(source.outputType()))")
  @Mapping(target = "hasCompensation", ignore = true)
  @Mapping(target = "description", source = "source.description")
  @Mapping(target = "inputSchema", ignore = true)
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
  DefinitionMetaDto toFunctionDefinitionMeta(DslDescriptor source,
          java.util.Map<String, Object> inputSchema);

  @Mapping(target = "type", constant = "helper")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "taskQueue", ignore = true)
  @Mapping(target = "inputType", expression = "java(typeName(descriptor.inputType()))")
  @Mapping(target = "outputType", expression = "java(typeName(descriptor.outputType()))")
  @Mapping(target = "hasCompensation", ignore = true)
  @Mapping(target = "description", ignore = true)
  @Mapping(target = "inputSchema", ignore = true)
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
  DefinitionMetaDto toHelperDefinitionMeta(String name, ExecutableDescriptor descriptor,
          java.util.Map<String, Object> inputSchema);
}
