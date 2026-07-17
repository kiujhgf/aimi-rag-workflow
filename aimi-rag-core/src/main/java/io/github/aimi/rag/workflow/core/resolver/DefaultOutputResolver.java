package io.github.aimi.rag.workflow.core.resolver;

import io.github.aimi.rag.workflow.core.model.FlowContext;

public class DefaultOutputResolver<O> implements OutputResolver<O> {

    private final String key;

    public DefaultOutputResolver(String key) {
        this.key = key;
    }

    @Override
    public void resolve(O output, FlowContext context) {
        context.set(key, output);
    }

    public static <O> DefaultOutputResolver<O> byKey(String key) {
        return new DefaultOutputResolver<>(key);
    }
}