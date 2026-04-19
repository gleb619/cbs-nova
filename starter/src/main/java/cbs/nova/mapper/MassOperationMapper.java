package cbs.nova.mapper;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.model.MassOperationDto;
import cbs.nova.model.MassOperationItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MassOperationMapper {

  @Mapping(target = "status", expression = "java(entity.getStatus().name())")
  MassOperationDto toDto(MassOperationExecutionEntity entity);

  @Mapping(target = "status", expression = "java(entity.getStatus().name())")
  MassOperationItemDto toItemDto(MassOperationItemEntity entity);
}
