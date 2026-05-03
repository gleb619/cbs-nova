package cbs.nova.repository;

import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowTransitionLogRepository
    extends JpaRepository<WorkflowTransitionLogEntity, Long> {

}
