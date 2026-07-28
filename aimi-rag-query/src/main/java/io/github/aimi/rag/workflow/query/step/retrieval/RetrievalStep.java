package io.github.aimi.rag.workflow.query.step.retrieval;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.query.model.RetrievalResult;

public abstract class RetrievalStep extends AbstractStep<float[], RetrievalResult> {

    protected RetrievalStep() {
        super();
    }

    protected RetrievalStep(InputResolver<float[]> inputResolver) {
        super(inputResolver);
    }

    protected RetrievalStep(InputResolver<float[]> inputResolver, OutputResolver<RetrievalResult> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public String getName() {
        return "retrieval";
    }

    @Override
    public StepType getType() {
        return StepType.RETRIEVAL;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.QUERY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<float[]> getInputType() {
        return float[].class;
    }

    @Override
    protected abstract RetrievalResult doProcess(float[] input, FlowContext context) throws StepExecuteException;
}
