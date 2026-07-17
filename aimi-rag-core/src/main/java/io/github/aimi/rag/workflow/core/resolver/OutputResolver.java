package io.github.aimi.rag.workflow.core.resolver;

import io.github.aimi.rag.workflow.core.model.FlowContext;

@FunctionalInterface
public interface OutputResolver<O> {
    void resolve(O output, FlowContext context);
}