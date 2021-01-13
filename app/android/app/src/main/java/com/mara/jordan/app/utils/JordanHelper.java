package com.mara.jordan.app.utils;

import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.model.dto.JordanMessageStateAuditDTO;
import com.mara.jordan.app.model.dto.JordanTaskDTO;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.math.Stats.meanOf;

public class JordanHelper implements JordanConstant {
    private static final JordanMessageStateAuditDTO DEFAULT_MESSAGE = JordanMessageStateAuditDTO.builder().build();
    public static final int NO_TASK_WITH_PROGRESS_FLAG = -1;

    /**
     * return items :
     * <ol>
     *     <li>number of active tasks (state in active_task_states.xml) in the client</li>
     * </ol>
     * @param client
     *      the client to examine, which may have active tasks
     * @param activeTaskStates
     *      task states considered as active (use  <code>getContext().getResources().getStringArray(R.array.active_task_states)</code>)
     */
    public static Object[] extractActiveTasksInfo(JordanClientDTO client, String[] activeTaskStates) {
        long activeTaskCount = getActiveTaskCount(client, activeTaskStates);
        return new Object[]{String.valueOf(activeTaskCount)};
    }

    public static long getActiveTaskCount(JordanClientDTO[] clients, String[] activeTaskStates) {
//        return Stream.of(clients).map(c -> getActiveTaskCount(c, activeTaskStates)).reduce(0l, Long::sum);
        long acc = 0L;
        for (JordanClientDTO c : clients) {
            long activeTaskCount = getActiveTaskCount(c, activeTaskStates);
            acc = acc + activeTaskCount;
        }
        return acc;
    }
    public static long getActiveTaskCount(JordanClientDTO client, String[] activeTaskStates) {
        //TODO count client as task ?
        //        long activeTaskCount = client.getTasks().stream().filter(t -> ArrayUtils.contains(activeTaskStates, t.getState())).count();
        long activeTaskCount = 0L;
        for (JordanTaskDTO t : client.getTasks()) {
            if (ArrayUtils.contains(activeTaskStates, t.getState())) {
                activeTaskCount++;
            }
        }
        return activeTaskCount;
    }

    /**
     * return items :
     * <ol>
     *     <li>number of active clients (state in active_client_states.xml)</li>
     * </ol>
     * @param clients
     *      the clients to examine
     * @param activeClientStates
     *      client states considered as active (use  <code>getContext().getResources().getStringArray(R.array.active_client_states)</code>)
     */ public static long getActiveClientCount(JordanClientDTO[] clients, String[] activeClientStates) {
//        long activeClientCount = Stream.of(clients).filter(c -> ArrayUtils.contains(activeClientStates, c.getState())).count();
        long activeClientCount = 0L;
        for (JordanClientDTO c : clients) {
            if (ArrayUtils.contains(activeClientStates, c.getState())) {
                activeClientCount++;
            }
        }
        return activeClientCount;
    }

    /**
     * This method is more restrictive than {@link JordanHelper#getActiveTaskCount(JordanClientDTO, String[])}
     * in the extend that the considered tasks must not only be active (same), but also having a non null progress (0 being non null).
     * @param client
     *      the client to examine, which may have active tasks
     * @param activeTaskStates
     *      task states considered as active (use  <code>getContext().getResources().getStringArray(R.array.active_task_states)</code>)
     * @return map of eligible tasks, associated with their progress
     */
    public static Map<JordanTaskDTO, Integer> extractActiveTasksProgress(JordanClientDTO client, String[] activeTaskStates) {
//        Map<JordanTaskDTO, Integer> eligibleTaskProgress = client.getTasks().stream()
//                .filter(t -> ArrayUtils.contains(activeTaskStates, t.getState()) && t.getProgress() != null)
//                .collect(Collectors.toMap(
//                        Function.identity(),
//                        JordanTaskDTO::getProgress
//                ));
        Map<JordanTaskDTO, Integer> eligibleTaskProgress = new HashMap<>();
        for (JordanTaskDTO t : client.getTasks()) {
            if (ArrayUtils.contains(activeTaskStates, t.getState()) && t.getProgress() != null) {
                if (eligibleTaskProgress.put(t, t.getProgress()) != null) {
                    throw new IllegalStateException("Duplicate key");
                }
            }
        }
        return eligibleTaskProgress;
    }

    public static Map<JordanTaskDTO, Integer> extractActiveTasksProgress(JordanClientDTO[] clients, String[] activeTaskStates) {
        Map<JordanTaskDTO, Integer> acc = new HashMap<>();
        for (JordanClientDTO c : clients) {
            Map<JordanTaskDTO, Integer> clientActiveTasks = extractActiveTasksProgress(c, activeTaskStates);
            acc.putAll(clientActiveTasks);
        }
        return acc;
    }

    public static int estimateClientProgress(JordanClientDTO[] clients, String[] activeTaskStates){
        Collection<Integer> eligibleTaskProgress = extractActiveTasksProgress(clients, activeTaskStates).values();
        if(eligibleTaskProgress.isEmpty()){
            return NO_TASK_WITH_PROGRESS_FLAG;
        }
        return (int) meanOf(eligibleTaskProgress); //no need to round
    }

    public static int estimateClientProgress(JordanClientDTO client, String[] activeTaskStates){
        Collection<Integer> eligibleTaskProgress = extractActiveTasksProgress(client, activeTaskStates).values();
        if(eligibleTaskProgress.isEmpty()){
            return NO_TASK_WITH_PROGRESS_FLAG;
        }
        return (int) meanOf(eligibleTaskProgress); //no need to round
    }
    /**
     * Get current state of a Jordan Message.
     * A Jordan Message keeps full audit of successive states.
     * So the current state is the last one.
     */
    public static String getCurrentState(List<JordanMessageStateAuditDTO> audit) {
        //or last element if the list list construction ensures chronology
//        return audit.stream().reduce((m1,m2) -> m1.getTimestamp() > m2.getTimestamp() ? m1 : m2).orElse(DEFAULT_MESSAGE).getState();
        boolean seen = false;
        JordanMessageStateAuditDTO acc = null;
        for (JordanMessageStateAuditDTO jordanMessageStateAuditDTO : audit) {
            if (!seen) {
                seen = true;
                acc = jordanMessageStateAuditDTO;
            } else {
                acc = acc.getTimestamp() > jordanMessageStateAuditDTO.getTimestamp() ? acc : jordanMessageStateAuditDTO;
            }
        }
        return (seen ? acc : DEFAULT_MESSAGE).getState();
    }
}
