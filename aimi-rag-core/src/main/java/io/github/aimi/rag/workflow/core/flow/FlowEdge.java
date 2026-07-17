package io.github.aimi.rag.workflow.core.flow;

public class FlowEdge {

    public static final String WILDCARD = "*";

    private final String status;

    public FlowEdge() {
        this.status = WILDCARD;
    }

    public FlowEdge(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public boolean isWildcard() {
        return WILDCARD.equals(status);
    }

    @Override
    public String toString() {
        return "FlowEdge{" + status + "}";
    }
}
