package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepStatus;

public interface FlowExecutionListener {

    default void onFlowStart(FlowContext context) {
    }

    default void onStepStart(String stepName, FlowContext context) {
    }

    default void onStepSuccess(String stepName, StepStatus status, FlowContext context) {
    }

    default void onStepFailure(String stepName, Throwable error, FlowContext context) {
    }

    default void onFlowComplete(FlowContext context) {
    }

    default void onFlowFailure(Throwable error, FlowContext context) {
    }
}
