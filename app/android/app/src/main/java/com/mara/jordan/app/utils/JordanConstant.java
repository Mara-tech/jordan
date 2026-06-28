package com.mara.jordan.app.utils;

import com.mara.jordan.core.JordanConstants;

/**
 * Backward-compatibility shim. Prefer {@link JordanConstants} directly.
 * @deprecated migrate call sites to {@link JordanConstants}
 */
@Deprecated
public interface JordanConstant {
    String TASK_COMPLETE_STATE    = JordanConstants.TASK_COMPLETE_STATE;
    String TASK_RUNNING_STATE     = JordanConstants.TASK_RUNNING_STATE;
    String TASK_STARTED_STATE     = JordanConstants.TASK_STARTED_STATE;
    String TASK_TIME_OUT_STATE    = JordanConstants.TASK_TIME_OUT_STATE;
    String CLIENT_REGISTERED_STATE   = JordanConstants.CLIENT_REGISTERED_STATE;
    String CLIENT_UNREGISTERED_STATE = JordanConstants.CLIENT_UNREGISTERED_STATE;
}
