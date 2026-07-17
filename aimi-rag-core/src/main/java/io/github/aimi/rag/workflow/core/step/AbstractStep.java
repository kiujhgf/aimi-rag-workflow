package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;

public abstract class AbstractStep<I, O> implements Step<I, O> {

    protected InputResolver<I> inputResolver;
    protected OutputResolver<O> outputResolver;
    protected StepStatus status = StepStatus.FINISHED;

    protected int retryTimes = 1;
    protected Class<? extends Throwable>[] retryableExceptions = new Class[]{Exception.class};

    protected AbstractStep() {
        this.inputResolver = new DefaultInputResolver<>(getInputType(), getName());
        this.outputResolver = new DefaultOutputResolver<>(getName());
    }

    protected AbstractStep(InputResolver<I> inputResolver) {
        this.inputResolver = inputResolver;
        this.outputResolver = new DefaultOutputResolver<>(getName());
    }

    protected AbstractStep(InputResolver<I> inputResolver, OutputResolver<O> outputResolver) {
        this.inputResolver = inputResolver;
        this.outputResolver = outputResolver;
    }

    protected void setInputResolver(InputResolver<I> inputResolver) {
        this.inputResolver = inputResolver;
    }

    protected void setOutputResolver(OutputResolver<O> outputResolver) {
        this.outputResolver = outputResolver;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.INGEST;
    }

    @Override
    public I resolveInput(FlowContext context) throws ResolveInputException {
        return inputResolver.resolve(context);
    }

    @Override
    public StepStatus process(FlowContext context) throws StepExecuteException {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < retryTimes) {
            try {
                I input = resolveInput(context);
                O output = doProcess(input, context);
                writeOutput(output, context);
                this.status = StepStatus.FINISHED;
                return this.status;
            } catch (Exception e) {
                lastException = e;
                attempts++;
                if (attempts >= retryTimes || !isRetryable(e)) {
                    this.status = StepStatus.FAILED;
                    throw new StepExecuteException(getName(), e);
                }
            }
        }

        this.status = StepStatus.FAILED;
        throw new StepExecuteException(getName(), lastException);
    }

    private boolean isRetryable(Exception e) {
        for (Class<? extends Throwable> clazz : retryableExceptions) {
            if (clazz.isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    protected abstract O doProcess(I input, FlowContext context) throws StepExecuteException;

    protected void writeOutput(O output, FlowContext context) {
        outputResolver.resolve(output, context);
    }

    @Override
    public StepStatus getStatus() {
        return status;
    }

    @Override
    public int getRetryTimes() {
        return retryTimes;
    }

    @Override
    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    @Override
    public Class<? extends Throwable>[] getRetryableExceptions() {
        return retryableExceptions;
    }
}
