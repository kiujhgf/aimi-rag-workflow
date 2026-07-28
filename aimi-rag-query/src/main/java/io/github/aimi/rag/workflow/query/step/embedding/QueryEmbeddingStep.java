package io.github.aimi.rag.workflow.query.step.embedding;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;

public abstract class QueryEmbeddingStep extends AbstractStep<String, float[]> {

    protected QueryEmbeddingStep() {
        super();
    }

    protected QueryEmbeddingStep(InputResolver<String> inputResolver) {
        super(inputResolver);
    }

    protected QueryEmbeddingStep(InputResolver<String> inputResolver, OutputResolver<float[]> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public String getName() {
        return "query-embedding";
    }

    @Override
    public StepType getType() {
        return StepType.EMBEDDING;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.QUERY;
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    protected abstract float[] doProcess(String input, FlowContext context) throws StepExecuteException;
}
