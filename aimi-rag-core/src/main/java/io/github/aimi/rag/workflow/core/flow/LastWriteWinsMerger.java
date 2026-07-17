package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.model.FlowContext;

import java.util.List;

public class LastWriteWinsMerger implements ContextMerger {

    @Override
    public void merge(FlowContext main, List<FlowContext> subContexts) {
        if (subContexts == null || subContexts.isEmpty()) {
            return;
        }
        for (FlowContext sub : subContexts) {
            if (sub == null || sub.isEmpty()) {
                continue;
            }
            sub.getAll().forEach(main::set);
        }
    }
}
