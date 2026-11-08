package ru.pvlapvp.ciactionsmonitor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionsStatusTest {

    @Test
    void mapsActiveStates() {
        assertEquals(ActionsStatus.QUEUED, ActionsStatus.from("queued", null));
        assertEquals(ActionsStatus.QUEUED, ActionsStatus.from("pending", null));
        assertEquals(ActionsStatus.WAITING, ActionsStatus.from("waiting", null));
        assertEquals(ActionsStatus.IN_PROGRESS, ActionsStatus.from("in_progress", null));
        assertEquals(ActionsStatus.IN_PROGRESS, ActionsStatus.from("running", null));
        assertEquals(ActionsStatus.IN_PROGRESS, ActionsStatus.from("cancelling", null));
    }

    @Test
    void mapsConclusionsWithPriorityOverStatus() {
        assertEquals(ActionsStatus.SUCCESS, ActionsStatus.from("completed", "success"));
        assertEquals(ActionsStatus.FAILED, ActionsStatus.from("completed", "failure"));
        assertEquals(ActionsStatus.FAILED, ActionsStatus.from("completed", "timed_out"));
        assertEquals(ActionsStatus.FAILED, ActionsStatus.from("completed", "startup_failure"));
        assertEquals(ActionsStatus.CANCELLED, ActionsStatus.from("completed", "cancelled"));
        assertEquals(ActionsStatus.SKIPPED, ActionsStatus.from("completed", "skipped"));
        assertEquals(ActionsStatus.NEUTRAL, ActionsStatus.from("completed", "neutral"));
        assertEquals(ActionsStatus.ACTION_REQUIRED, ActionsStatus.from("completed", "action_required"));
        assertEquals(ActionsStatus.STALE, ActionsStatus.from("completed", "stale"));
    }

    @Test
    void completedWithoutConclusionIsUnknown() {
        assertEquals(ActionsStatus.UNKNOWN, ActionsStatus.from("completed", null));
        assertEquals(ActionsStatus.UNKNOWN, ActionsStatus.from("completed", "unexpected"));
    }

    @Test
    void unknownAndNullValuesDoNotBreak() {
        assertEquals(ActionsStatus.UNKNOWN, ActionsStatus.from("something-new", null));
        assertEquals(ActionsStatus.UNKNOWN, ActionsStatus.from(null, null));
        assertEquals(ActionsStatus.UNKNOWN, ActionsStatus.from("  ", "  "));
    }

    @Test
    void mappingIsCaseInsensitive() {
        assertEquals(ActionsStatus.SUCCESS, ActionsStatus.from("COMPLETED", "SUCCESS"));
        assertEquals(ActionsStatus.IN_PROGRESS, ActionsStatus.from("IN_PROGRESS", null));
    }

    @Test
    void activeAndFinishedFlags() {
        assertTrue(ActionsStatus.IN_PROGRESS.isActive());
        assertFalse(ActionsStatus.IN_PROGRESS.isFinished());
        assertTrue(ActionsStatus.FAILED.isFinished());
        assertFalse(ActionsStatus.FAILED.isActive());
        assertFalse(ActionsStatus.UNKNOWN.isActive());
        assertFalse(ActionsStatus.UNKNOWN.isFinished());
    }
}
