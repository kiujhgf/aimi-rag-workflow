package io.github.aimi.rag.workflow.core.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowEdgeTest {

    @Test
    void shouldDefaultToWildcard() {
        FlowEdge edge = new FlowEdge();
        assertEquals("*", edge.getStatus());
        assertTrue(edge.isWildcard());
    }

    @Test
    void shouldSetCustomStatus() {
        FlowEdge edge = new FlowEdge("success");
        assertEquals("success", edge.getStatus());
        assertFalse(edge.isWildcard());
    }

    @Test
    void shouldSetLoopStatus() {
        FlowEdge edge = new FlowEdge("LOOP");
        assertEquals("LOOP", edge.getStatus());
        assertFalse(edge.isWildcard());
    }

    @Test
    void shouldHaveWildcardConstant() {
        assertEquals("*", FlowEdge.WILDCARD);
    }

    @Test
    void shouldHaveMeaningfulToString() {
        FlowEdge edge = new FlowEdge("custom");
        String str = edge.toString();
        assertTrue(str.contains("custom"));
    }
}
