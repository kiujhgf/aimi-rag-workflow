package io.github.aimi.rag.workflow.log.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "flow_execution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowExecution {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "flow_name", length = 128, nullable = false)
    private String flowName;

    @Column(length = 32, nullable = false)
    private String status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "input_context", columnDefinition = "TEXT")
    private String inputContext;
}