package com.mara.jordan.app.adapter;

import com.mara.jordan.core.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.core.dto.JordanParentTaskDTO;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActionComparatorTest {

    private final ActionComparator comparator = new ActionComparator();

    private static JordanActionDefinitionWithTaskDTO action(String actionName, String taskState) {
        return JordanActionDefinitionWithTaskDTO.builder()
                .actionName(actionName)
                .parentTask(JordanParentTaskDTO.builder().state(taskState).taskId(1L).build())
                .build();
    }

    @Test
    public void bothNull_returnsZero() {
        assertEquals(0, comparator.compare(null, null));
    }

    @Test
    public void firstNull_isLessThanNonNull() {
        JordanActionDefinitionWithTaskDTO nonNull = action("a", "RUNNING");
        assertTrue(comparator.compare(null, nonNull) < 0);
    }

    @Test
    public void secondNull_isGreaterThanNonNull() {
        JordanActionDefinitionWithTaskDTO nonNull = action("a", "RUNNING");
        assertTrue(comparator.compare(nonNull, null) > 0);
    }

    @Test
    public void runningBeforeStarted() {
        JordanActionDefinitionWithTaskDTO running = action("a", "RUNNING");
        JordanActionDefinitionWithTaskDTO started = action("b", "STARTED");
        assertTrue(comparator.compare(running, started) < 0);
    }

    @Test
    public void startedBeforeComplete() {
        JordanActionDefinitionWithTaskDTO started = action("a", "STARTED");
        JordanActionDefinitionWithTaskDTO complete = action("b", "COMPLETE");
        assertTrue(comparator.compare(started, complete) < 0);
    }

    @Test
    public void completeBeforeTimeout() {
        JordanActionDefinitionWithTaskDTO complete = action("a", "COMPLETE");
        JordanActionDefinitionWithTaskDTO timeout = action("b", "TIME_OUT");
        assertTrue(comparator.compare(complete, timeout) < 0);
    }

    @Test
    public void timeoutBeforeUnknownState() {
        JordanActionDefinitionWithTaskDTO timeout = action("a", "TIME_OUT");
        JordanActionDefinitionWithTaskDTO unknown = action("b", "UNKNOWN");
        assertTrue(comparator.compare(timeout, unknown) < 0);
    }

    @Test
    public void sameState_sortsByActionNameAlphabetically() {
        JordanActionDefinitionWithTaskDTO alpha = action("alpha", "RUNNING");
        JordanActionDefinitionWithTaskDTO zeta = action("zeta", "RUNNING");
        assertTrue(comparator.compare(alpha, zeta) < 0);
        assertTrue(comparator.compare(zeta, alpha) > 0);
    }

    @Test
    public void equalObjects_returnsZero() {
        JordanActionDefinitionWithTaskDTO a = action("alpha", "RUNNING");
        JordanActionDefinitionWithTaskDTO b = action("alpha", "RUNNING");
        assertEquals(0, comparator.compare(a, b));
    }

    @Test
    public void nullParentTask_sortsBeforeNonNull() {
        JordanActionDefinitionWithTaskDTO withTask = action("a", "RUNNING");
        JordanActionDefinitionWithTaskDTO noTask = JordanActionDefinitionWithTaskDTO.builder()
                .actionName("b")
                .parentTask(null)
                .build();
        assertTrue(comparator.compare(noTask, withTask) < 0);
    }
}
