package cbs.nova.repository;

import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {

}
