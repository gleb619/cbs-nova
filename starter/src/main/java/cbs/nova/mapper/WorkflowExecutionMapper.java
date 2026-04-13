package cbs.nova.mapper;

import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.model.WorkflowExecutionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkflowExecutionMapper {

  @Mapping(target = "status", expression = "java(entity.getStatus().name())")
  WorkflowExecutionDto toDto(WorkflowExecutionEntity entity);
}
