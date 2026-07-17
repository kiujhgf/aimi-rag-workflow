package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.flow.ContextMerger;
import io.github.aimi.rag.workflow.core.flow.LastWriteWinsMerger;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepType;

import java.util.ArrayList;
import java.util.List;

public class ParallelEndStep extends AbstractStep<Void, Void> {

    private final ContextMerger merger;
    private final List<FlowContext> subContexts = new ArrayList<>();

    public ParallelEndStep() {
        this(new LastWriteWinsMerger());
    }

    public ParallelEndStep(ContextMerger merger) {
        this.merger = merger != null ? merger : new LastWriteWinsMerger();
    }

    public void addSubContext(FlowContext subContext) {
        subContexts.add(subContext);
    }

    public void clearSubContexts() {
        subContexts.clear();
    }

    @Override
    public String getName() {
        return "PARALLEL_END";
    }

    @Override
    public StepType getType() {
        return StepType.END;
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
        merger.merge(context, subContexts);
        return null;
    }

    @Override
    protected void writeOutput(Void output, FlowContext context) {
    }
}
