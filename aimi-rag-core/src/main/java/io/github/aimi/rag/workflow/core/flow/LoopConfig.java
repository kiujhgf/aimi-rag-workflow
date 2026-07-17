package io.github.aimi.rag.workflow.core.flow;

public class LoopConfig {

    private int maxIterations = 20;

    public LoopConfig() {
    }

    public LoopConfig(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public static LoopConfig defaultConfig() {
        return new LoopConfig();
    }

    public static LoopConfig max(int maxIterations) {
        return new LoopConfig(maxIterations);
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
}
