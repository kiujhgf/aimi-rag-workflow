package io.github.aimi.rag.workflow.core.resolver;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.model.FlowContext;

@FunctionalInterface
public interface InputResolver<I> {
    I resolve(FlowContext context) throws ResolveInputException;
}