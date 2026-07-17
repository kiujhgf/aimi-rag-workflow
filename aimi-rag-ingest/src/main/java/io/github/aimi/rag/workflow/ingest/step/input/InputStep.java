package io.github.aimi.rag.workflow.ingest.step.input;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;

import java.util.List;

public abstract class InputStep<T> extends AbstractStep<T, List<String>> {

    protected InputStep() {
        super();
    }

    protected InputStep(InputResolver<T> inputResolver) {
        super(inputResolver);
    }

    protected InputStep(InputResolver<T> inputResolver, OutputResolver<List<String>> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public StepType getType() {
        return StepType.INPUT;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.INGEST;
    }

    @Override
    public Class<T> getInputType() {
        throw new UnsupportedOperationException("InputStep requires explicit inputResolver; getInputType is not supported");
    }

    @Override
    protected abstract List<String> doProcess(T input, io.github.aimi.rag.workflow.core.model.FlowContext context) throws StepExecuteException;
}