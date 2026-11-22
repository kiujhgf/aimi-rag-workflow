package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.FlowContext;

import java.util.List;

/**
 * Repository for persisting flow execution state.
 * Implementations provide concrete storage and are used by execution listeners
 * to persist lifecycle events.
 *
 * <p>The execution id is read from the {@link FlowContext} (under
 * {@link io.github.aimi.rag.workflow.core.model.FlowContextKeys#EXECUTION_ID})
 * rather than passed as a parameter, since {@code Flow.execute()} generates it.
 */
public interface FlowExecutionRepository {

    /**
     * Create a new flow execution record using the execution id already stored
     * in the context.
     *
     * @param flowName     name of the flow
     * @param inputContext the initial input context carrying the execution id
     */
    void createExecution(String flowName, FlowContext inputContext);

    /**
     * Save a successful step execution record.
     *
     * @param context the flow context carrying the execution id
     * @param record  step execution details
     */
    void saveStepExecution(FlowContext context, StepExecutionRecord record);

    /**
     * Save a step failure record.
     *
     * @param context the flow context carrying the execution id
     * @param record  step failure details
     */
    void saveStepFailure(FlowContext context, StepFailureRecord record);

    /**
     * Update the overall execution status (RUNNING, COMPLETED, FAILED).
     *
     * @param context the flow context carrying the execution id
     * @param status  new execution status
     */
    void updateExecutionStatus(FlowContext context, String status);

    /**
     * Get the execution history (all step records) for a flow execution.
     *
     * @param executionId flow execution id
     * @return list of step execution records in execution order
     */
    List<StepExecutionRecord> getExecutionHistory(String executionId);
}
