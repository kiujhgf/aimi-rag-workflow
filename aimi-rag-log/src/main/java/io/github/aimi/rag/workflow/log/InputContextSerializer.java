package io.github.aimi.rag.workflow.log;

import io.github.aimi.rag.workflow.core.model.FlowContext;

@FunctionalInterface
public interface InputContextSerializer {

    String serialize(FlowContext context);

    static InputContextSerializer none() {
        return context -> null;
    }
}
