package io.github.aimi.rag.workflow.core.flow;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowDecider;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.core.step.Step;

import java.util.Objects;

public class FlowNode {

    private final Step<?, ?> step;
    private final FlowDecider decider;
    private final String name;
    private LoopConfig loopConfig;

    public FlowNode(Step<?, ?> step) {
        this.step = step;
        this.decider = null;
        this.name = step.getName();
    }

    public FlowNode(FlowDecider decider, String name) {
        this.step = null;
        this.decider = decider;
        this.name = name;
    }

    public FlowNode(FlowDecider decider, String name, LoopConfig loopConfig) {
        this.step = null;
        this.decider = decider;
        this.name = name;
        this.loopConfig = loopConfig;
    }

    public boolean isStep() {
        return step != null;
    }

    public boolean isDecider() {
        return decider != null;
    }

    public Step<?, ?> getStep() {
        return step;
    }

    public FlowDecider getDecider() {
        return decider;
    }

    public String getName() {
        return name;
    }

    public LoopConfig getLoopConfig() {
        return loopConfig;
    }

    public StepStatus execute(FlowContext context) throws StepExecuteException {
        if (step != null) {
            return step.process(context);
        }
        return StepStatus.FINISHED;
    }

    public String decide(FlowContext context) {
        if (decider != null) {
            return decider.decide(context);
        }
        return "*";
    }

    public StepStatus getStatus() {
        if (step != null) {
            return step.getStatus();
        }
        return StepStatus.FINISHED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowNode flowNode = (FlowNode) o;
        if (step != null) {
            return step.equals(flowNode.step);
        }
        return Objects.equals(name, flowNode.name);
    }

    @Override
    public int hashCode() {
        return step != null ? step.hashCode() : Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return "FlowNode{" + name + "}";
    }
}
