package cbs.nova.repository;

import cbs.nova.entity.MassOperationExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MassOperationExecutionRepository
    extends JpaRepository<MassOperationExecutionEntity, Long> {

  List<MassOperationExecutionEntity> findByCode(String code);
}
