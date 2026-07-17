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
public class StepFailure {

    private String id;
    private String executionId;
    private String stepName;
    private String errorMessage;
    private String errorStack;
    private Instant timestamp;
}