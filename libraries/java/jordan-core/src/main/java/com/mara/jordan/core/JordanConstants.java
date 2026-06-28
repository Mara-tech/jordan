package com.mara.jordan.core;

public final class JordanConstants {

    // Task states
    public static final String TASK_STARTED_STATE = "STARTED";
    public static final String TASK_RUNNING_STATE = "RUNNING";
    public static final String TASK_COMPLETE_STATE = "COMPLETE";
    public static final String TASK_ERROR_STATE = "ERROR";
    public static final String TASK_TIME_OUT_STATE = "TIME_OUT";
    public static final String TASK_PAUSED_STATE = "PAUSED";

    // Client states
    public static final String CLIENT_REGISTERED_STATE = "REGISTERED";
    public static final String CLIENT_UNREGISTERED_STATE = "UNREGISTERED";

    // Status types
    public static final String STATUS_TYPE_GENERAL = "general";
    public static final String STATUS_TYPE_PROGRESS = "progress";
    public static final String STATUS_TYPE_SUCCESS = "success";
    public static final String STATUS_TYPE_FAILURE = "failure";

    // Message states (in state machine order)
    public static final String MESSAGE_STATE_SERVER_RECEIVED = "SERVER_RECEIVED";
    public static final String MESSAGE_STATE_DELIVERED = "MESSAGE_DELIVERED";
    public static final String MESSAGE_STATE_CLIENT_RECEIVED = "CLIENT_RECEIVED";
    public static final String MESSAGE_STATE_ACKNOWLEDGED = "MESSAGE_ACKNOWLEDGED";
    public static final String MESSAGE_STATE_PROCESSED = "MESSAGE_PROCESSED";
    public static final String MESSAGE_STATE_ERROR_CANNOT_PROCESS = "ERROR_CANNOT_PROCESS_MESSAGE";
    public static final String MESSAGE_STATE_OVERRIDDEN = "MESSAGE_OVERRIDDEN";

    // Action parameter types
    public static final String PARAMETER_TYPE_STRING = "string";
    public static final String PARAMETER_TYPE_INT = "int";
    public static final String PARAMETER_TYPE_FLOAT = "float";

    private JordanConstants() {}
}
