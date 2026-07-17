package io.github.aimi.rag.workflow.log.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimi.rag.workflow.core.flow.FlowExecutionRepository;
import io.github.aimi.rag.workflow.core.flow.StepExecutionRecord;
import io.github.aimi.rag.workflow.core.flow.StepFailureRecord;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.log.jpa.entity.FlowExecution;
import io.github.aimi.rag.workflow.log.jpa.entity.StepExecution;
import io.github.aimi.rag.workflow.log.jpa.entity.StepFailure;
import io.github.aimi.rag.workflow.log.jpa.repository.FlowExecutionJpaRepository;
import io.github.aimi.rag.workflow.log.jpa.repository.StepExecutionJpaRepository;
import io.github.aimi.rag.workflow.log.jpa.repository.StepFailureJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JpaFlowExecutionRepository implements FlowExecutionRepository {

    private final FlowExecutionJpaRepository flowExecutionJpaRepository;
    private final StepExecutionJpaRepository stepExecutionJpaRepository;
    private final StepFailureJpaRepository stepFailureJpaRepository;
    private final ObjectMapper objectMapper;

    public JpaFlowExecutionRepository(FlowExecutionJpaRepository flowExecutionJpaRepository,
                                      StepExecutionJpaRepository stepExecutionJpaRepository,
                                      StepFailureJpaRepository stepFailureJpaRepository,
                                      ObjectMapper objectMapper) {
        this.flowExecutionJpaRepository = flowExecutionJpaRepository;
        this.stepExecutionJpaRepository = stepExecutionJpaRepository;
        this.stepFailureJpaRepository = stepFailureJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public String createExecution(String flowName, FlowContext inputContext) {
        String executionId = UUID.randomUUID().toString();
        FlowExecution flowExecution = FlowExecution.builder()
                .id(executionId)
                .flowName(flowName)
                .status("RUNNING")
                .startTime(Instant.now())
                .inputContext(serializeContext(inputContext))
                .build();
        flowExecutionJpaRepository.save(flowExecution);
        return executionId;
    }

    @Override
    @Transactional
    public void saveStepExecution(String executionId, StepExecutionRecord record) {
        StepExecution stepExecution = StepExecution.builder()
                .id(UUID.randomUUID().toString())
                .executionId(executionId)
                .stepName(record.getStepName())
                .status(record.getStatus().name())
                .startTime(record.getStartTime())
                .endTime(record.getEndTime())
                .build();
        stepExecutionJpaRepository.save(stepExecution);
    }

    @Override
    @Transactional
    public void saveStepFailure(String executionId, StepFailureRecord record) {
        StepFailure stepFailure = StepFailure.builder()
                .id(UUID.randomUUID().toString())
                .executionId(executionId)
                .stepName(record.getStepName())
                .errorMessage(record.getErrorMessage())
                .errorStack(record.getErrorStack())
                .timestamp(record.getTimestamp())
                .build();
        stepFailureJpaRepository.save(stepFailure);
    }

    @Override
    @Transactional
    public void updateExecutionStatus(String executionId, String status) {
        flowExecutionJpaRepository.findById(executionId).ifPresent(flowExecution -> {
            flowExecution.setStatus(status);
            flowExecution.setEndTime(Instant.now());
            flowExecutionJpaRepository.save(flowExecution);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<StepExecutionRecord> getExecutionHistory(String executionId) {
        return stepExecutionJpaRepository.findByExecutionIdOrderByStartTime(executionId)
                .stream()
                .map(this::toStepExecutionRecord)
                .collect(Collectors.toList());
    }

    private StepExecutionRecord toStepExecutionRecord(StepExecution entity) {
        return StepExecutionRecord.builder()
                .executionId(entity.getExecutionId())
                .stepName(entity.getStepName())
                .status(StepStatus.valueOf(entity.getStatus()))
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .build();
    }

    private String serializeContext(FlowContext context) {
        if (context == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(context.getAll());
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}