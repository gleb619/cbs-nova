package cbs.nova.repository;

import cbs.nova.entity.EventExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventExecutionRepository extends JpaRepository<EventExecutionEntity, Long> {}
