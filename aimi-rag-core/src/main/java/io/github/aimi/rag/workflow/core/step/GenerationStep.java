package io.github.aimi.rag.workflow.core.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Base generation step: takes prepared chat messages and produces the raw model answer.
 * Prompt assembly and result mapping are delegated to the configured resolvers.
 */
public abstract class GenerationStep extends AbstractStep<List<Message>, String> {

    protected GenerationStep() {
        super();
    }

    protected GenerationStep(InputResolver<List<Message>> inputResolver) {
        super(inputResolver);
    }

    protected GenerationStep(InputResolver<List<Message>> inputResolver, OutputResolver<String> outputResolver) {
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
    public Class<List<Message>> getInputType() {
        return (Class<List<Message>>) (Class<?>) List.class;
    }

    @Override
    protected abstract String doProcess(List<Message> input, FlowContext context) throws StepExecuteException;
}
