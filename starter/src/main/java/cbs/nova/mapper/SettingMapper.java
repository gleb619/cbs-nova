package cbs.nova.mapper;

import cbs.nova.entity.SettingEntity;
import cbs.nova.model.SettingCreateDto;
import cbs.nova.model.SettingDto;
import cbs.nova.model.SettingUpdateDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface SettingMapper {

  SettingDto toDto(SettingEntity entity);

  @Mapping(target = "id", ignore = true)
  SettingEntity toEntity(SettingCreateDto dto);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "code", source = "dto.code")
  @Mapping(target = "value", source = "dto.value")
  @Mapping(target = "description", source = "dto.description")
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void update(SettingUpdateDto source, @MappingTarget SettingEntity entity);
}
