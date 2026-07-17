package io.github.aimi.rag.workflow.log.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "step_failure")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepFailure {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "execution_id", length = 64, nullable = false)
    private String executionId;

    @Column(name = "step_name", length = 128, nullable = false)
    private String stepName;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack", columnDefinition = "TEXT")
    private String errorStack;

    @Column(nullable = false)
    private Instant timestamp;
}