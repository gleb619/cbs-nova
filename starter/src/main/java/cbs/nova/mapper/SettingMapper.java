package cbs.nova.mapper;

import cbs.nova.entity.Setting;
import cbs.nova.model.SettingCreateDto;
import cbs.nova.model.SettingDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface SettingMapper {

  SettingDto toDto(Setting entity);

  @Mapping(target = "id", ignore = true)
  Setting toEntity(SettingCreateDto dto);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void update(SettingCreateDto dto, @MappingTarget Setting entity);
}
