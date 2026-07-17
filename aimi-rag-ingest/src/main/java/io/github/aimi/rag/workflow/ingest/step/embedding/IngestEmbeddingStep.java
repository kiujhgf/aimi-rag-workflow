package io.github.aimi.rag.workflow.ingest.step.embedding;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.exception.StepValidationException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.core.step.Step;
import io.github.aimi.rag.workflow.ingest.model.Chunk;
import io.github.aimi.rag.workflow.ingest.model.Embedding;

import java.util.List;

public abstract class IngestEmbeddingStep extends AbstractStep<List<Chunk>, List<Embedding>> {

    protected IngestEmbeddingStep() {
        super();
    }

    protected IngestEmbeddingStep(InputResolver<List<Chunk>> inputResolver) {
        super(inputResolver);
    }

    protected IngestEmbeddingStep(InputResolver<List<Chunk>> inputResolver, OutputResolver<List<Embedding>> outputResolver) {
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
    public Class<List<Chunk>> getInputType() {
        return (Class<List<Chunk>>) (Class<?>) List.class;
    }

    @Override
    protected abstract List<Embedding> doProcess(List<Chunk> input, FlowContext context) throws StepExecuteException;
}