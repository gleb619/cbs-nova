package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslRunEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JDBC repository for {@link DslRunEntity} reads.
 *
 * <p>
 * No {@code @Repository} annotation is used: Spring Data JDBC creates the implementation bean
 * automatically during repository scanning.
 */
// TODO: we need to limit package scan for repositories, only to `cbs.nova.starter.persistence`
public interface DslRunJdbcRepository extends CrudRepository<DslRunEntity, Long> {

  Optional<DslRunEntity> findByRunId(String runId);

  List<DslRunEntity> findByProcessName(String processName);
}
