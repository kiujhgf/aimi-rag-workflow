package io.github.aimi.rag.workflow.core.flow;

public class FlowEdge {

    public static final String WILDCARD = "*";

    /**
     * Reserved status for error-routing edges created by onFailure().
     * Error edges are never selected during normal routing.
     */
    public static final String ERROR = "ERROR";

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

    public boolean isError() {
        return ERROR.equals(status);
    }

    @Override
    public String toString() {
        return "FlowEdge{" + status + "}";
    }
}
