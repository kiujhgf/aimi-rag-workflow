package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;

import java.time.Instant;

/**
 * Listener that persists flow execution state via FlowExecutionRepository.
 * The executionId is stored in FlowContext under {@link #EXECUTION_ID_KEY}
 * and can be retrieved by downstream listeners.
 *
 * <p>Inject a FlowExecutionRepository bean and register this listener
 * via {@code flow.addListener(new PersistentExecutionListener(repository))}.
 */
public class PersistentExecutionListener implements FlowExecutionListener {

    /** Context key under which the execution id is stored. */
    public static final String EXECUTION_ID_KEY = "__execution_id";

    /** Context key prefix for per-step start timestamps. */
    private static final String STEP_START_KEY_PREFIX = "__step_start:";

    private final FlowExecutionRepository repository;
    private final String flowName;

    public PersistentExecutionListener(FlowExecutionRepository repository) {
        this(repository, "unnamed-flow");
    }

    public PersistentExecutionListener(FlowExecutionRepository repository, String flowName) {
        this.repository = repository;
        this.flowName = flowName;
    }

    @Override
    public void onFlowStart(FlowContext context) {
        String executionId = repository.createExecution(flowName, context);
        context.set(EXECUTION_ID_KEY, executionId);
        repository.updateExecutionStatus(executionId, "RUNNING");
    }

    @Override
    public void onStepStart(String stepName, FlowContext context) {
        context.set(STEP_START_KEY_PREFIX + stepName, Instant.now());
    }

    @Override
    public void onStepSuccess(String stepName, StepStatus status, FlowContext context) {
        String executionId = context.get(EXECUTION_ID_KEY, String.class);
        if (executionId == null) {
            return;
        }

        Instant startTime = context.get(STEP_START_KEY_PREFIX + stepName, Instant.class);
        Instant endTime = Instant.now();

        StepExecutionRecord record = StepExecutionRecord.builder()
                .executionId(executionId)
                .stepName(stepName)
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .build();

        repository.saveStepExecution(executionId, record);
    }

    @Override
    public void onStepFailure(String stepName, Throwable error, FlowContext context) {
        String executionId = context.get(EXECUTION_ID_KEY, String.class);
        if (executionId == null) {
            return;
        }

        StepFailureRecord record = StepFailureRecord.builder()
                .executionId(executionId)
                .stepName(stepName)
                .errorMessage(error.getMessage())
                .errorStack(getStackTrace(error))
                .timestamp(Instant.now())
                .build();

        repository.saveStepFailure(executionId, record);
    }

    @Override
    public void onFlowComplete(FlowContext context) {
        String executionId = context.get(EXECUTION_ID_KEY, String.class);
        if (executionId != null) {
            repository.updateExecutionStatus(executionId, "COMPLETED");
        }
    }

    @Override
    public void onFlowFailure(Throwable error, FlowContext context) {
        String executionId = context.get(EXECUTION_ID_KEY, String.class);
        if (executionId != null) {
            repository.updateExecutionStatus(executionId, "FAILED");
        }
    }

    private static String getStackTrace(Throwable error) {
        if (error == null) {
            return null;
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
