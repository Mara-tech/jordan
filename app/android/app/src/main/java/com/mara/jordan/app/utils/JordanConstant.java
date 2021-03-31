package com.mara.jordan.app.utils;

public interface JordanConstant {

    /*
    Check res/values/active_client_states and res/values/active_task_states
    for states considered as "Active"
     */

    /**
     * Add specific drawable for COMPLETE task.
     */
    String TASK_COMPLETE_STATE = "COMPLETE";
    String TASK_RUNNING_STATE = "RUNNING";
    String TASK_STARTED_STATE = "STARTED";
    String TASK_TIME_OUT_STATE = "TIME_OUT";

    String CLIENT_REGISTERED_STATE = "REGISTERED";
    String CLIENT_UNREGISTERED_STATE = "UNREGISTERED";



}
