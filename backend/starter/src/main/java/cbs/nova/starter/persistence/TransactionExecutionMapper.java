package cbs.nova.starter.persistence;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link TransactionExecution} (domain/DTO) and
 * {@link TransactionExecutionEntity}.
 *
 * <p>
 * JSON serialization of the {@code input} field is handled by the repository layer.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransactionExecutionMapper {

  @Mapping(target = "inputJson", ignore = true)
  TransactionExecutionEntity toEntity(TransactionExecution execution);

  TransactionExecution toDomain(TransactionExecutionEntity entity);
}
