package com.mara.jordan.client;

import com.google.gson.Gson;
import com.mara.jordan.core.JordanConstants;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JordanInstance implements Closeable {

    protected static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    protected static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0], null);

    protected final String baseUrl;
    protected final long taskId;
    protected final String authToken;
    protected final String name;
    protected final OkHttpClient httpClient;
    protected final Gson gson;

    JordanInstance(String baseUrl, long taskId, String authToken, String name, OkHttpClient httpClient, Gson gson) {
        this.baseUrl = baseUrl;
        this.taskId = taskId;
        this.authToken = authToken;
        this.name = name;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public JordanTaskInstance createTask(String taskName) throws IOException {
        return createTask(taskName, Collections.<Map<String, Object>>emptyList(), null);
    }

    public JordanTaskInstance createTask(String taskName, List<Map<String, Object>> actions) throws IOException {
        return createTask(taskName, actions, null);
    }

    public JordanTaskInstance createTask(String taskName, List<Map<String, Object>> actions, String password) throws IOException {
        String url = String.format("%sclient/%d/task", baseUrl, taskId);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", taskName);
        if (password != null) payload.put("password", password);
        if (!actions.isEmpty()) payload.put("actions", actions);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .post(RequestBody.create(gson.toJson(payload), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 201) {
                Map result = gson.fromJson(response.body().string(), Map.class);
                long newTaskId = ((Number) result.get("taskId")).longValue();
                return new JordanTaskInstance(baseUrl, newTaskId, authToken, taskName, httpClient, gson);
            }
            throw new IOException("createTask failed: HTTP " + response.code() + " - " + response.body().string());
        }
    }

    public String sendStatus(String status) throws IOException {
        return sendStatus(status, JordanConstants.STATUS_TYPE_GENERAL);
    }

    public String sendStatus(String status, String statusType) throws IOException {
        String url = String.format("%sclient/%d/status", baseUrl, taskId);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("type", statusType);
        payload.put("status", status);
        payload.put("timestamp", System.currentTimeMillis() / 1000L);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .post(RequestBody.create(gson.toJson(payload), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200) {
                Map result = gson.fromJson(response.body().string(), Map.class);
                return String.valueOf(((Number) result.get("statusId")).longValue());
            }
            throw new IOException("sendStatus failed: HTTP " + response.code() + " - " + response.body().string());
        }
    }

    public String sendProgress(String status) throws IOException {
        return sendStatus(status, JordanConstants.STATUS_TYPE_PROGRESS);
    }

    public String sendSuccessStatus(String status) throws IOException {
        return sendStatus(status, JordanConstants.STATUS_TYPE_SUCCESS);
    }

    public String sendFailureStatus(String status) throws IOException {
        return sendStatus(status, JordanConstants.STATUS_TYPE_FAILURE);
    }

    public JordanMessage readMessage() throws IOException {
        String url = String.format("%sclient/%d/message", baseUrl, taskId);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200) {
                Map data = gson.fromJson(response.body().string(), Map.class);
                JordanMessage msg = new JordanMessage(baseUrl, taskId, authToken, data, httpClient, gson);
                msg.received();
                return msg;
            }
            return null;
        }
    }

    public boolean updateTask(String taskState) throws IOException {
        String url = String.format("%sclient/%d/%s", baseUrl, taskId, taskState);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .put(EMPTY_BODY)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.code() == 202;
        }
    }

    public boolean complete() throws IOException {
        return updateTask(JordanConstants.TASK_COMPLETE_STATE);
    }

    public boolean unregister() throws IOException {
        String url = String.format("%sclient/%d/unregister", baseUrl, taskId);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .post(EMPTY_BODY)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.code() == 200;
        }
    }

    public void fatal(Exception e) throws IOException {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        sendFailureStatus(msg);
        updateTask(JordanConstants.TASK_ERROR_STATE);
        unregister();
    }

    @Override
    public void close() throws IOException {
        unregister();
    }

    public long getTaskId() { return taskId; }
    public String getName() { return name; }
}
