package io.github.aimi.rag.workflow.log.jpa.repository;

import io.github.aimi.rag.workflow.log.jpa.entity.StepExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StepExecutionJpaRepository extends JpaRepository<StepExecution, String> {
    
    List<StepExecution> findByExecutionIdOrderByStartTime(String executionId);
}