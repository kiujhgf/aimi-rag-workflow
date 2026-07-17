package io.github.aimi.rag.workflow.ingest.step.input;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.DefaultOutputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;

import java.util.List;

public class StringInputStep extends InputStep<List<String>> {

    private StringInputStep(InputResolver<List<String>> inputResolver, OutputResolver<List<String>> outputResolver) {
        super(inputResolver, outputResolver);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getName() {
        return "string-input";
    }

    @Override
    protected List<String> doProcess(List<String> input, FlowContext context) throws StepExecuteException {
        return input;
    }

    public static class Builder {
        private InputResolver<List<String>> inputResolver = new DefaultInputResolver.ByKeyResolver<>("input", List.class, "string-input");
        private OutputResolver<List<String>> outputResolver = new DefaultOutputResolver<>("texts");

        private Builder() {}

        public Builder inputResolver(InputResolver<List<String>> inputResolver) {
            this.inputResolver = inputResolver;
            return this;
        }

        public Builder outputResolver(OutputResolver<List<String>> outputResolver) {
            this.outputResolver = outputResolver;
            return this;
        }

        public StringInputStep build() {
            return new StringInputStep(inputResolver, outputResolver);
        }
    }
}