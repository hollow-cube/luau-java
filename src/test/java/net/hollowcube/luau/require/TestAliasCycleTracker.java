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
    void testCycleBackToTheFirstAlias() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("a"));
        assertNull(tracker.add("b"));
        assertNull(tracker.add("c"));
        assertEquals(
                "detected alias cycle (@a -> @b -> @c -> @a)",
                tracker.add("a")
        );
    }

    /// The alias which closed a cycle is not recorded, so a later report does not mention
    /// it twice.
    @Test
    void testRejectedAddIsNotRemembered() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("a"));
        assertNull(tracker.add("b"));
        assertEquals("detected alias cycle (@a -> @b -> @a)", tracker.add("a"));

        assertNull(tracker.add("c"));
        assertEquals(
                "detected alias cycle (@b -> @c -> @b)",
                tracker.add("b")
        );
    }

    @Test
    void testAliasesAreComparedExactly() {
        var tracker = new AliasCycleTracker();
        assertNull(tracker.add("alias"));
        assertNull(tracker.add("Alias"));
        assertNull(tracker.add("alia"));
    }

    /// Trackers are independent: a fresh one is handed out for each alias resolution.
    @Test
    void testTrackersDoNotShareState() {
        var first = new AliasCycleTracker();
        var second = new AliasCycleTracker();
        assertNull(first.add("shared"));
        assertNull(second.add("shared"));
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
