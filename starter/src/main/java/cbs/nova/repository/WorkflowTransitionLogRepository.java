package cbs.nova.repository;

import cbs.nova.entity.WorkflowTransitionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionLogRepository
    extends JpaRepository<WorkflowTransitionLogEntity, Long> {

}
