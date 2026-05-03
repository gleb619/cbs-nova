package cbs.nova.repository;

import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MassOperationExecutionRepository
    extends JpaRepository<MassOperationExecutionEntity, Long> {

  List<MassOperationExecutionEntity> findByCode(String code);

}
