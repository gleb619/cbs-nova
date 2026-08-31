package cbs.nova.starter.converter;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.starter.entity.DslRunEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DslRunMapper {

  @Mapping(target = "inputJson", source = "input")
  @Mapping(target = "outputJson", source = "output")
  @Mapping(target = "errorMessage", source = "error")
  @Mapping(target = "contextJson", source = "contextJson")
  @Mapping(target = "triggeredBy", source = "triggeredBy")
  @Mapping(target = "correlationId", source = "correlationId")
  DslRunEntity toEntity(DslRun run);

  @Mapping(target = "input", source = "inputJson")
  @Mapping(target = "output", source = "outputJson")
  @Mapping(target = "error", source = "errorMessage")
  @Mapping(target = "contextJson", source = "contextJson")
  @Mapping(target = "triggeredBy", source = "triggeredBy")
  @Mapping(target = "correlationId", source = "correlationId")
  DslRun toDomain(DslRunEntity entity);
}
