package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParallelStartStep extends AbstractStep<Void, Void> {

    private ExecutorService executorService;
    private boolean internalExecutor = false;

    public ParallelStartStep() {
        this(null);
    }

    public ParallelStartStep(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public String getName() {
        return "PARALLEL_START";
    }

    @Override
    public StepType getType() {
        return StepType.START;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.INGEST;
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Void resolveInput(FlowContext context) throws ResolveInputException {
        return null;
    }

    @Override
    protected Void doProcess(Void input, FlowContext context) {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
            internalExecutor = true;
        }
        return null;
    }

    @Override
    protected void writeOutput(Void output, FlowContext context) {
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public boolean isInternalExecutor() {
        return internalExecutor;
    }

    public void shutdownExecutor() {
        if (internalExecutor && executorService != null) {
            executorService.shutdown();
        }
    }
}
