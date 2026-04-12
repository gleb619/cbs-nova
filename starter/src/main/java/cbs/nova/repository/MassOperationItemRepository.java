package cbs.nova.repository;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.entity.MassOperationItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MassOperationItemRepository extends JpaRepository<MassOperationItemEntity, Long> {

  List<MassOperationItemEntity> findByMassOperationExecution(
      MassOperationExecutionEntity execution);

  List<MassOperationItemEntity> findByMassOperationExecutionAndStatus(
      MassOperationExecutionEntity execution, MassOperationItemStatus status);
}
