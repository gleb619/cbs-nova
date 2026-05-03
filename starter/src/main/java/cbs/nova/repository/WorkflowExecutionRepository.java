package cbs.nova.repository;

import cbs.nova.entity.WorkflowExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {

}
