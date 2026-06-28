package com.mara.jordan.core;

import com.mara.jordan.core.dto.JordanClientDTO;
import com.mara.jordan.core.dto.JordanMessageStateAuditDTO;
import com.mara.jordan.core.dto.JordanTaskDTO;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JordanHelper {

    public static final int NO_TASK_WITH_PROGRESS_FLAG = -1;

    private static final JordanMessageStateAuditDTO DEFAULT_MESSAGE = new JordanMessageStateAuditDTO();

    /**
     * @param activeTaskStates pass {@code getContext().getResources().getStringArray(R.array.active_task_states)} on Android
     */
    public static Object[] extractActiveTasksInfo(JordanClientDTO client, String[] activeTaskStates) {
        return new Object[]{String.valueOf(getActiveTaskCount(client, activeTaskStates))};
    }

    public static long getActiveTaskCount(JordanClientDTO[] clients, String[] activeTaskStates) {
        long acc = 0L;
        for (JordanClientDTO c : clients) {
            acc += getActiveTaskCount(c, activeTaskStates);
        }
        return acc;
    }

    public static long getActiveTaskCount(JordanClientDTO client, String[] activeTaskStates) {
        long count = 0L;
        if (client.getTasks() != null && !client.getTasks().isEmpty()) {
            for (JordanTaskDTO t : client.getTasks()) {
                if (Arrays.asList(activeTaskStates).contains(t.getState())) {
                    count++;
                }
            }
        }
        return count;
    }

    public static long getActiveClientCount(JordanClientDTO[] clients, String[] activeClientStates) {
        long count = 0L;
        List<String> states = Arrays.asList(activeClientStates);
        for (JordanClientDTO c : clients) {
            if (states.contains(c.getState())) {
                count++;
            }
        }
        return count;
    }

    public static Map<JordanTaskDTO, Integer> extractActiveTasksProgress(JordanClientDTO client, String[] activeTaskStates) {
        Map<JordanTaskDTO, Integer> result = new HashMap<>();
        if (client.getTasks() != null && !client.getTasks().isEmpty()) {
            List<String> states = Arrays.asList(activeTaskStates);
            for (JordanTaskDTO t : client.getTasks()) {
                if (states.contains(t.getState()) && t.getProgress() != null) {
                    result.put(t, t.getProgress());
                }
            }
        }
        return result;
    }

    public static Map<JordanTaskDTO, Integer> extractActiveTasksProgress(JordanClientDTO[] clients, String[] activeTaskStates) {
        Map<JordanTaskDTO, Integer> acc = new HashMap<>();
        for (JordanClientDTO c : clients) {
            acc.putAll(extractActiveTasksProgress(c, activeTaskStates));
        }
        return acc;
    }

    public static int estimateClientProgress(JordanClientDTO[] clients, String[] activeTaskStates) {
        Collection<Integer> values = extractActiveTasksProgress(clients, activeTaskStates).values();
        if (values.isEmpty()) return NO_TASK_WITH_PROGRESS_FLAG;
        return mean(values);
    }

    public static int estimateClientProgress(JordanClientDTO client, String[] activeTaskStates) {
        Collection<Integer> values = extractActiveTasksProgress(client, activeTaskStates).values();
        if (values.isEmpty()) return NO_TASK_WITH_PROGRESS_FLAG;
        return mean(values);
    }

    /** Returns the current (most recent) state from a message audit trail. */
    public static String getCurrentState(List<JordanMessageStateAuditDTO> audit) {
        JordanMessageStateAuditDTO latest = null;
        for (JordanMessageStateAuditDTO entry : audit) {
            if (latest == null || entry.getTimestamp() > latest.getTimestamp()) {
                latest = entry;
            }
        }
        return (latest != null ? latest : DEFAULT_MESSAGE).getState();
    }

    private static int mean(Collection<Integer> values) {
        int sum = 0;
        for (int v : values) sum += v;
        return sum / values.size();
    }

    private JordanHelper() {}
}
