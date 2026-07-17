package io.github.aimi.rag.workflow.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StepStatusTest {

    @Test
    void shouldHaveThreeValues() {
        StepStatus[] values = StepStatus.values();
        assertEquals(3, values.length);
    }

    @Test
    void shouldContainFinishedFailedEnd() {
        assertTrue(contains(StepStatus.values(), StepStatus.FINISHED));
        assertTrue(contains(StepStatus.values(), StepStatus.FAILED));
        assertTrue(contains(StepStatus.values(), StepStatus.END));
    }

    @Test
    void shouldBeComparableViaEquals() {
        assertEquals(StepStatus.FINISHED, StepStatus.valueOf("FINISHED"));
        assertNotEquals(StepStatus.FINISHED, StepStatus.FAILED);
        assertNotEquals(StepStatus.FAILED, StepStatus.END);
    }

    @Test
    void valueOfShouldReturnCorrectEnum() {
        assertSame(StepStatus.FINISHED, StepStatus.valueOf("FINISHED"));
        assertSame(StepStatus.FAILED, StepStatus.valueOf("FAILED"));
        assertSame(StepStatus.END, StepStatus.valueOf("END"));
    }

    private boolean contains(StepStatus[] arr, StepStatus target) {
        for (StepStatus s : arr) {
            if (s == target) return true;
        }
        return false;
    }
}
