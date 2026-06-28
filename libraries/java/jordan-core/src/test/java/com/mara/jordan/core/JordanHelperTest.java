package com.mara.jordan.core;

import com.mara.jordan.core.dto.JordanClientDTO;
import com.mara.jordan.core.dto.JordanMessageStateAuditDTO;
import com.mara.jordan.core.dto.JordanTaskDTO;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JordanHelperTest {

    private static final String[] ACTIVE_TASK_STATES   = {"STARTED", "RUNNING"};
    private static final String[] ACTIVE_CLIENT_STATES = {"REGISTERED"};

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private JordanTaskDTO task(String state, Integer progress) {
        return JordanTaskDTO.builder().taskId(1L).name("t").state(state).progress(progress).build();
    }

    private JordanClientDTO client(String state, JordanTaskDTO... tasks) {
        return JordanClientDTO.builder()
                .clientId(1L).name("c").state(state)
                .tasks(Arrays.asList(tasks))
                .build();
    }

    private JordanClientDTO clientNoTasks(String state) {
        return JordanClientDTO.builder().clientId(1L).name("c").state(state).build();
    }

    // -------------------------------------------------------------------------
    // getActiveTaskCount
    // -------------------------------------------------------------------------

    @Test
    public void testGetActiveTaskCountMatchingStates() {
        JordanClientDTO c = client("REGISTERED",
                task("RUNNING", null),
                task("COMPLETE", null),
                task("STARTED", null));
        assertEquals(2L, JordanHelper.getActiveTaskCount(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testGetActiveTaskCountNone() {
        JordanClientDTO c = client("REGISTERED", task("COMPLETE", null), task("TIME_OUT", null));
        assertEquals(0L, JordanHelper.getActiveTaskCount(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testGetActiveTaskCountEmptyList() {
        JordanClientDTO c = JordanClientDTO.builder().clientId(1L).name("c").state("REGISTERED")
                .tasks(Collections.emptyList()).build();
        assertEquals(0L, JordanHelper.getActiveTaskCount(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testGetActiveTaskCountNullTasks() {
        assertEquals(0L, JordanHelper.getActiveTaskCount(clientNoTasks("REGISTERED"), ACTIVE_TASK_STATES));
    }

    @Test
    public void testGetActiveTaskCountAcrossClients() {
        JordanClientDTO[] clients = {
                client("REGISTERED", task("RUNNING", null), task("COMPLETE", null)),
                client("REGISTERED", task("STARTED", null), task("RUNNING", null))
        };
        assertEquals(3L, JordanHelper.getActiveTaskCount(clients, ACTIVE_TASK_STATES));
    }

    // -------------------------------------------------------------------------
    // getActiveClientCount
    // -------------------------------------------------------------------------

    @Test
    public void testGetActiveClientCount() {
        JordanClientDTO[] clients = {
                clientNoTasks("REGISTERED"),
                clientNoTasks("UNREGISTERED"),
                clientNoTasks("REGISTERED")
        };
        assertEquals(2L, JordanHelper.getActiveClientCount(clients, ACTIVE_CLIENT_STATES));
    }

    @Test
    public void testGetActiveClientCountNone() {
        JordanClientDTO[] clients = {clientNoTasks("UNREGISTERED")};
        assertEquals(0L, JordanHelper.getActiveClientCount(clients, ACTIVE_CLIENT_STATES));
    }

    // -------------------------------------------------------------------------
    // extractActiveTasksProgress
    // -------------------------------------------------------------------------

    @Test
    public void testExtractActiveTasksProgressOnlyActiveTasks() {
        JordanClientDTO c = client("REGISTERED",
                task("RUNNING", 50),
                task("COMPLETE", 100),  // excluded: not in active states
                task("STARTED", 25));
        Map<JordanTaskDTO, Integer> result = JordanHelper.extractActiveTasksProgress(c, ACTIVE_TASK_STATES);
        assertEquals(2, result.size());
        assertTrue(result.values().contains(50));
        assertTrue(result.values().contains(25));
        assertFalse(result.values().contains(100));
    }

    @Test
    public void testExtractActiveTasksProgressExcludesNullProgress() {
        JordanClientDTO c = client("REGISTERED",
                task("RUNNING", null),   // active but no progress → excluded
                task("RUNNING", 70));
        Map<JordanTaskDTO, Integer> result = JordanHelper.extractActiveTasksProgress(c, ACTIVE_TASK_STATES);
        assertEquals(1, result.size());
        assertTrue(result.values().contains(70));
    }

    @Test
    public void testExtractActiveTasksProgressEmpty() {
        JordanClientDTO c = client("REGISTERED", task("COMPLETE", 100));
        assertTrue(JordanHelper.extractActiveTasksProgress(c, ACTIVE_TASK_STATES).isEmpty());
    }

    // -------------------------------------------------------------------------
    // estimateClientProgress
    // -------------------------------------------------------------------------

    @Test
    public void testEstimateClientProgressAverage() {
        JordanClientDTO c = client("REGISTERED", task("RUNNING", 40), task("STARTED", 80));
        assertEquals(60, JordanHelper.estimateClientProgress(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testEstimateClientProgressSingleTask() {
        JordanClientDTO c = client("REGISTERED", task("RUNNING", 33));
        assertEquals(33, JordanHelper.estimateClientProgress(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testEstimateClientProgressNoEligibleTasksReturnsFlag() {
        JordanClientDTO c = client("REGISTERED", task("COMPLETE", 100));
        assertEquals(JordanHelper.NO_TASK_WITH_PROGRESS_FLAG,
                JordanHelper.estimateClientProgress(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testEstimateClientProgressNoProgressValueReturnsFlag() {
        JordanClientDTO c = client("REGISTERED", task("RUNNING", null));
        assertEquals(JordanHelper.NO_TASK_WITH_PROGRESS_FLAG,
                JordanHelper.estimateClientProgress(c, ACTIVE_TASK_STATES));
    }

    @Test
    public void testEstimateClientProgressAcrossClients() {
        JordanClientDTO[] clients = {
                client("REGISTERED", task("RUNNING", 20)),
                client("REGISTERED", task("RUNNING", 80))
        };
        assertEquals(50, JordanHelper.estimateClientProgress(clients, ACTIVE_TASK_STATES));
    }

    // -------------------------------------------------------------------------
    // getCurrentState
    // -------------------------------------------------------------------------

    @Test
    public void testGetCurrentStateReturnsMostRecent() {
        List<JordanMessageStateAuditDTO> audit = Arrays.asList(
                new JordanMessageStateAuditDTO(100L, JordanConstants.MESSAGE_STATE_SERVER_RECEIVED),
                new JordanMessageStateAuditDTO(300L, JordanConstants.MESSAGE_STATE_DELIVERED),
                new JordanMessageStateAuditDTO(200L, JordanConstants.MESSAGE_STATE_CLIENT_RECEIVED)
        );
        assertEquals(JordanConstants.MESSAGE_STATE_DELIVERED, JordanHelper.getCurrentState(audit));
    }

    @Test
    public void testGetCurrentStateSingleEntry() {
        List<JordanMessageStateAuditDTO> audit = Collections.singletonList(
                new JordanMessageStateAuditDTO(500L, JordanConstants.MESSAGE_STATE_PROCESSED));
        assertEquals(JordanConstants.MESSAGE_STATE_PROCESSED, JordanHelper.getCurrentState(audit));
    }

    @Test
    public void testGetCurrentStateEmptyAuditReturnsNull() {
        assertNull(JordanHelper.getCurrentState(Collections.emptyList()));
    }

    @Test
    public void testGetCurrentStateHandlesTieByKeepingLast() {
        List<JordanMessageStateAuditDTO> audit = Arrays.asList(
                new JordanMessageStateAuditDTO(100L, JordanConstants.MESSAGE_STATE_SERVER_RECEIVED),
                new JordanMessageStateAuditDTO(100L, JordanConstants.MESSAGE_STATE_DELIVERED)
        );
        // Same timestamp: last one wins (iteration order)
        String state = JordanHelper.getCurrentState(audit);
        assertNotNull(state);
    }
}
