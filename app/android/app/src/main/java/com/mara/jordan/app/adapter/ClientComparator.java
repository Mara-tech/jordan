package com.mara.jordan.app.adapter;

import com.google.common.collect.ImmutableList;
import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.model.dto.JordanTaskDTO;
import com.mara.jordan.app.utils.JordanConstant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ClientComparator implements Comparator<JordanClientDTO>, JordanConstant {

    private static final List<String> STATE_ORDER = ImmutableList.of(
            TASK_RUNNING_STATE,
            TASK_STARTED_STATE,
            CLIENT_REGISTERED_STATE,
            TASK_COMPLETE_STATE,
            TASK_TIME_OUT_STATE,
            CLIENT_UNREGISTERED_STATE,
            ClientAdapter.DEFAULT_STATE
    );

    @Override
    public int compare(JordanClientDTO o1, JordanClientDTO o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if (o2 == null)
            return 1;
        if(o1.equals(o2))
            return 0;

        int byState = compareByState(o1.getState(), o2.getState());
        if(byState != 0)
            return byState;

        int byTasks = compareByTasks(o1.getTasks(), o2.getTasks());
        if(byTasks != 0)
            return byTasks;

        int byName = compareByName(o1.getName(), o2.getName());
        if(byName != 0)
            return byName;

        return 0;
    }

    private static int compareByTasks(List<JordanTaskDTO> o1, List<JordanTaskDTO> o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if(o2 == null)
            return 1;
        if(o1.isEmpty() && o2.isEmpty())
            return 0;

        // use com.mara.jordan.app.utils.JordanHelper.extractActiveTasksProgress(com.mara.jordan.app.model.dto.JordanClientDTO, java.lang.String[]) ?
        int p1 = sumProgress(o1);
        int p2 = sumProgress(o2);

        if(p1==p2){
            return compareByTasks(allSubtasks(o1), allSubtasks(o2));
        }
        return -Integer.compare(p1, p2); // minus !
    }

    private static List<JordanTaskDTO> allSubtasks(List<JordanTaskDTO> tasks) {
//        return tasks.stream().flatMap(t->t.getTasks().stream()).collect(Collectors.toList());
        List<JordanTaskDTO> list = new ArrayList<>();
        for (JordanTaskDTO t : tasks) {
            list.addAll(t.getTasks());
        }
        return list;
    }

    private static int sumProgress(List<JordanTaskDTO> tasks) {
//        return tasks.stream().map(JordanTaskDTO::getProgress).reduce(Integer::sum);
        int acc = 0;
        for (JordanTaskDTO task : tasks) {
            Integer progress = task.getProgress();
            if(progress != null){
                acc += progress;
            }
        }
        return acc;
    }

    private static int compareByName(String o1, String o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if(o2 == null)
            return 1;
        return o1.compareTo(o2);
    }

    private static int compareByState(String o1, String o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if (o2 == null)
            return 1;
        if(o1.equals(o2))
            return 0;

        return STATE_ORDER.indexOf(o1) - STATE_ORDER.indexOf(o2);
    }

}
