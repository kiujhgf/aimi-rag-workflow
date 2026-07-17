package io.github.aimi.rag.workflow.log.mybatis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecution {

    private String id;
    private String executionId;
    private String stepName;
    private String status;
    private Instant startTime;
    private Instant endTime;
}