package io.github.aimi.rag.workflow.log.jpa.repository;

import io.github.aimi.rag.workflow.log.jpa.entity.FlowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowExecutionJpaRepository extends JpaRepository<FlowExecution, String> {
}