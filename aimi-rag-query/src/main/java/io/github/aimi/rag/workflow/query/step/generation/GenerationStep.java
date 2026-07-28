package io.github.aimi.rag.workflow.query.step.generation;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.query.model.QueryResult;
import io.github.aimi.rag.workflow.query.model.RetrievalResult;

public abstract class GenerationStep extends AbstractStep<RetrievalResult, QueryResult> {

    protected GenerationStep() {
        super();
    }

    protected GenerationStep(InputResolver<RetrievalResult> inputResolver) {
        super(inputResolver);
    }

    protected GenerationStep(InputResolver<RetrievalResult> inputResolver, OutputResolver<QueryResult> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public String getName() {
        return "generation";
    }

    @Override
    public StepType getType() {
        return StepType.GENERATION;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.QUERY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<RetrievalResult> getInputType() {
        return RetrievalResult.class;
    }

    @Override
    protected abstract QueryResult doProcess(RetrievalResult input, FlowContext context) throws StepExecuteException;
}
