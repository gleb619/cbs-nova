package cbs.nova.starter.persistence;

import cbs.nova.dsl.DslRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link DslRun} (domain/DTO) and {@link DslRunEntity}.
 *
 * <p>
 * Encryption/decryption is applied by the repository layer around these conversions.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DslRunMapper {

  @Mapping(target = "inputJson", source = "input")
  @Mapping(target = "outputJson", source = "output")
  @Mapping(target = "errorMessage", source = "error")
  @Mapping(target = "contextJson", source = "contextJson")
  DslRunEntity toEntity(DslRun run);

  @Mapping(target = "input", source = "inputJson")
  @Mapping(target = "output", source = "outputJson")
  @Mapping(target = "error", source = "errorMessage")
  @Mapping(target = "contextJson", source = "contextJson")
  DslRun toDomain(DslRunEntity entity);
}
