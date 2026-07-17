package io.github.aimi.rag.workflow.core.model;

@FunctionalInterface
public interface FlowDecider {

    String decide(FlowContext context);
}