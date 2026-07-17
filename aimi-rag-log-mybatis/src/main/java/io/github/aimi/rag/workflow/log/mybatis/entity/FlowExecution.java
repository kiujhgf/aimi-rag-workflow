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
public class FlowExecution {

    private String id;
    private String flowName;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private String inputContext;
}