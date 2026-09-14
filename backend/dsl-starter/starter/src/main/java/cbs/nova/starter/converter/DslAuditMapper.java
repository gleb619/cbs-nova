package cbs.nova.starter.converter;

import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.model.DslAudit;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DslAuditMapper {

  DslAuditEntity toEntity(DslAudit audit);

  DslAudit toDomain(DslAuditEntity entity);
}
