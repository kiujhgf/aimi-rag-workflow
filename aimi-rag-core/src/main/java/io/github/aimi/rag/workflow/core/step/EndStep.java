package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepType;

public class EndStep extends AbstractStep<Void, Void> {

    public static final EndStep INSTANCE = new EndStep();

    private EndStep() {
    }

    @Override
    public String getName() {
        return "END";
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
        return null;
    }

    @Override
    protected void writeOutput(Void output, FlowContext context) {
    }
}
