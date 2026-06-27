package com.mara.jordan.app.adapter;

import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.model.dto.JordanTaskDTO;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClientComparatorTest {

    private final ClientComparator comparator = new ClientComparator();

    private static JordanClientDTO client(String name, String state) {
        return JordanClientDTO.builder()
                .clientId(1L)
                .name(name)
                .state(state)
                .tasks(Collections.emptyList())
                .build();
    }

    private static JordanTaskDTO task(int progress) {
        return JordanTaskDTO.builder()
                .taskId(1L)
                .progress(progress)
                .tasks(Collections.emptyList())
                .build();
    }

    @Test
    public void bothNull_returnsZero() {
        assertEquals(0, comparator.compare(null, null));
    }

    @Test
    public void firstNull_isLessThanNonNull() {
        assertTrue(comparator.compare(null, client("a", "RUNNING")) < 0);
    }

    @Test
    public void secondNull_isGreaterThanNonNull() {
        assertTrue(comparator.compare(client("a", "RUNNING"), null) > 0);
    }

    @Test
    public void runningBeforeStarted() {
        assertTrue(comparator.compare(client("a", "RUNNING"), client("b", "STARTED")) < 0);
    }

    @Test
    public void startedBeforeRegistered() {
        assertTrue(comparator.compare(client("a", "STARTED"), client("b", "REGISTERED")) < 0);
    }

    @Test
    public void registeredBeforeComplete() {
        assertTrue(comparator.compare(client("a", "REGISTERED"), client("b", "COMPLETE")) < 0);
    }

    @Test
    public void completeBeforeTimeout() {
        assertTrue(comparator.compare(client("a", "COMPLETE"), client("b", "TIME_OUT")) < 0);
    }

    @Test
    public void timeoutBeforeUnregistered() {
        assertTrue(comparator.compare(client("a", "TIME_OUT"), client("b", "UNREGISTERED")) < 0);
    }

    @Test
    public void unregisteredBeforeUnknownState() {
        assertTrue(comparator.compare(client("a", "UNREGISTERED"), client("b", "UNKNOWN")) < 0);
    }

    @Test
    public void sameState_higherProgressComesFirst() {
        JordanClientDTO highProgress = JordanClientDTO.builder()
                .name("high")
                .state("RUNNING")
                .tasks(Arrays.asList(task(80), task(50)))
                .build();
        JordanClientDTO lowProgress = JordanClientDTO.builder()
                .name("low")
                .state("RUNNING")
                .tasks(Arrays.asList(task(10)))
                .build();
        // higher total progress (130) sorts before lower (10) — minus sign in compareByTasks
        assertTrue(comparator.compare(highProgress, lowProgress) < 0);
    }

    @Test
    public void sameStateAndProgress_sortsByNameAlphabetically() {
        JordanClientDTO alpha = client("alpha", "RUNNING");
        JordanClientDTO zeta = client("zeta", "RUNNING");
        assertTrue(comparator.compare(alpha, zeta) < 0);
        assertTrue(comparator.compare(zeta, alpha) > 0);
    }

    @Test
    public void equalClients_returnsZero() {
        JordanClientDTO a = client("same", "RUNNING");
        JordanClientDTO b = client("same", "RUNNING");
        assertEquals(0, comparator.compare(a, b));
    }
}
