package io.github.aimi.rag.workflow.log.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "step_execution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecution {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "execution_id", length = 64, nullable = false)
    private String executionId;

    @Column(name = "step_name", length = 128, nullable = false)
    private String stepName;

    @Column(length = 32, nullable = false)
    private String status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;
}