package net.hollowcube.luau.require;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAliasCycleTracker {

    @Test
    void testSuccessfulAdd() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("first"));
        assertNull(tracker.add("second"));
    }

    @Test
    void testSimpleCycle() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("first"));
        assertEquals(
                "detected alias cycle (@first -> @first)",
                tracker.add("first")
        );
    }

    @Test
    void testMultipleAliasesCycle() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("first"));
        assertNull(tracker.add("second"));
        assertNull(tracker.add("third"));
        assertEquals(
                "detected alias cycle (@second -> @third -> @second)",
                tracker.add("second")
        );
    }

    @Test
    void testComplexCycle() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("a"));
        assertNull(tracker.add("b"));
        assertNull(tracker.add("c"));
        assertNull(tracker.add("d"));
        assertEquals(
                "detected alias cycle (@b -> @c -> @d -> @b)",
                tracker.add("b")
        );
    }

}
