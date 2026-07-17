package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.model.StepType;

public interface Step<I, O> {

    String getName();

    StepType getType();

    FlowCategory getCategory();

    I resolveInput(FlowContext context) throws ResolveInputException;

    Class<I> getInputType();

    StepStatus process(FlowContext context) throws StepExecuteException;

    StepStatus getStatus();

    int getRetryTimes();

    void setRetryTimes(int retryTimes);

    Class<? extends Throwable>[] getRetryableExceptions();
}
