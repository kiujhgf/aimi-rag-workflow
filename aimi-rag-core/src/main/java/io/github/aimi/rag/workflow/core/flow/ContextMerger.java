package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.FlowContext;

import java.util.List;

@FunctionalInterface
public interface ContextMerger {

    void merge(FlowContext main, List<FlowContext> subContexts);
}
