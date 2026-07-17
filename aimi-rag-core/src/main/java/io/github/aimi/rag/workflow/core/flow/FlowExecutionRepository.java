package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.FlowContext;

import java.util.List;

/**
 * Repository for persisting flow execution state.
 * Implementations (e.g. JDBC, JPA) provide concrete storage.
 * Injected into PersistentExecutionListener via Spring configuration.
 */
public interface FlowExecutionRepository {

    /**
     * Create a new flow execution record.
     *
     * @param flowName     name of the flow
     * @param inputContext the initial input context
     * @return the generated execution id
     */
    String createExecution(String flowName, FlowContext inputContext);

    /**
     * Save a successful step execution record.
     *
     * @param executionId flow execution id
     * @param record      step execution details
     */
    void saveStepExecution(String executionId, StepExecutionRecord record);

    /**
     * Save a step failure record.
     *
     * @param executionId flow execution id
     * @param record      step failure details
     */
    void saveStepFailure(String executionId, StepFailureRecord record);

    /**
     * Update the overall execution status (RUNNING, COMPLETED, FAILED).
     *
     * @param executionId flow execution id
     * @param status      new execution status
     */
    void updateExecutionStatus(String executionId, String status);

    /**
     * Get the execution history (all step records) for a flow execution.
     *
     * @param executionId flow execution id
     * @return list of step execution records in execution order
     */
    List<StepExecutionRecord> getExecutionHistory(String executionId);
}
