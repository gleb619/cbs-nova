package cbs.nova.repository;

import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.WorkflowExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventExecutionRepository extends JpaRepository<EventExecutionEntity, Long> {

}
