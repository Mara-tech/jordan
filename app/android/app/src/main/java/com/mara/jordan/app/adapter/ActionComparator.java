package com.mara.jordan.app.adapter;

import com.google.common.collect.ImmutableList;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;
import com.mara.jordan.app.utils.JordanConstant;

import java.util.Comparator;
import java.util.List;

public class ActionComparator implements Comparator<JordanActionDefinitionWithTaskDTO>, JordanConstant {

    private static final List<String> STATE_ORDER = ImmutableList.of(
            TASK_RUNNING_STATE,
            TASK_STARTED_STATE,
            TASK_COMPLETE_STATE,
            TASK_TIME_OUT_STATE,
            ClientAdapter.DEFAULT_STATE
    );

    @Override
    public int compare(JordanActionDefinitionWithTaskDTO o1, JordanActionDefinitionWithTaskDTO o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if (o2 == null)
            return 1;
        if(o1.equals(o2))
            return 0;

        int byParentTask = compareByParentTask(o1.getParentTask(), o2.getParentTask());
        if(byParentTask != 0)
            return byParentTask;

        int byName = compareByName(o1.getActionName(), o2.getActionName());
        if(byName != 0)
            return byName;

        return 0;
    }

    private static int compareByParentTask(JordanParentTaskDTO o1, JordanParentTaskDTO o2) {
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

        int byProgress = compareByProgress(o1.getProgress(), o2.getProgress());
        if(byProgress != 0)
            return byProgress;

        int byName = compareByName(o1.getName(), o2.getName());
        if(byName != 0)
            return byName;

        return Long.compare(o1.getTaskId(), o2.getTaskId());
    }

    private static int compareByProgress(Integer o1, Integer o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if (o2 == null)
            return 1;
        if(o1.equals(o2))
            return 0;

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

    private static int compareByName(String o1, String o2) {
        if(o1 == null && o2 == null)
            return 0;
        if(o1 == null)
            return -1;
        if(o2 == null)
            return 1;
        return o1.compareTo(o2);
    }
}
