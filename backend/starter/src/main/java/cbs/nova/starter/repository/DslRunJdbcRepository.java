package cbs.nova.starter.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DslRunJdbcRepository extends CrudRepository<DslRunEntity, Long> {

  Optional<DslRunEntity> findByRunId(String runId);

  List<DslRunEntity> findByProcessName(String processName);
}
