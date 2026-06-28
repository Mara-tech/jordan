package com.mara.jordan.client;

import com.google.gson.Gson;
import com.mara.jordan.core.JordanConstants;
import okhttp3.OkHttpClient;

import java.io.IOException;

/** A sub-task instance. Unlike {@link JordanInstance}, {@code fatal()} does not unregister the parent client. */
public class JordanTaskInstance extends JordanInstance {

    JordanTaskInstance(String baseUrl, long taskId, String authToken, String name, OkHttpClient httpClient, Gson gson) {
        super(baseUrl, taskId, authToken, name, httpClient, gson);
    }

    @Override
    public void fatal(Exception e) throws IOException {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        sendFailureStatus(msg);
        updateTask(JordanConstants.TASK_ERROR_STATE);
    }

    @Override
    public void close() {
        // sub-tasks are completed or marked as error; they are not unregistered
    }
}
