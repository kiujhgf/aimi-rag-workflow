package io.github.aimi.rag.workflow.ingest.step.embedding;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;

import java.util.List;

public abstract class IngestEmbeddingStep extends AbstractStep<List<String>, List<float[]>> {

    protected IngestEmbeddingStep() {
        super();
    }

    protected IngestEmbeddingStep(InputResolver<List<String>> inputResolver) {
        super(inputResolver);
    }

    protected IngestEmbeddingStep(InputResolver<List<String>> inputResolver, OutputResolver<List<float[]>> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public String getName() {
        return "embedding";
    }

    @Override
    public StepType getType() {
        return StepType.EMBEDDING;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.INGEST;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<String>> getInputType() {
        return (Class<List<String>>) (Class<?>) List.class;
    }

    @Override
    protected abstract List<float[]> doProcess(List<String> input, FlowContext context) throws StepExecuteException;
}
