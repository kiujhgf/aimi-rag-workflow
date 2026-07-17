package io.github.aimi.rag.workflow.ingest.step.chunking;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.exception.StepValidationException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.core.step.Step;
import io.github.aimi.rag.workflow.ingest.model.Chunk;

import java.util.List;

public abstract class ChunkingStep extends AbstractStep<List<String>, List<Chunk>> {

    protected ChunkingStep() {
        super();
    }

    protected ChunkingStep(InputResolver<List<String>> inputResolver) {
        super(inputResolver);
    }

    protected ChunkingStep(InputResolver<List<String>> inputResolver, OutputResolver<List<Chunk>> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public StepType getType() {
        return StepType.CHUNKING;
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
    protected abstract List<Chunk> doProcess(List<String> input, io.github.aimi.rag.workflow.core.model.FlowContext context) throws StepExecuteException;
}