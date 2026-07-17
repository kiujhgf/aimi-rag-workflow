package io.github.aimi.rag.workflow.log.jpa.repository;

import io.github.aimi.rag.workflow.log.jpa.entity.StepFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StepFailureJpaRepository extends JpaRepository<StepFailure, String> {
}