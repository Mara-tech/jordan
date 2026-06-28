package com.mara.jordan.client;

import com.google.gson.Gson;
import com.mara.jordan.core.JordanConstants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class JordanMessage {

    private final String baseUrl;
    private final long taskId;
    private final String authToken;
    private final long messageId;
    private final String actionName;
    private final Map<String, Object> placeholders;
    private final OkHttpClient httpClient;

    @SuppressWarnings("unchecked")
    JordanMessage(String baseUrl, long taskId, String authToken, Map data, OkHttpClient httpClient, Gson gson) {
        this.baseUrl = baseUrl;
        this.taskId = taskId;
        this.authToken = authToken;
        this.httpClient = httpClient;
        this.messageId = ((Number) data.get("messageId")).longValue();
        Map action = (Map) data.get("action");
        this.actionName = (String) action.get("actionName");
        Map ph = (Map) action.get("placeholders");
        this.placeholders = ph != null ? (Map<String, Object>) ph : Collections.<String, Object>emptyMap();
    }

    private boolean updateState(String state) throws IOException {
        String url = String.format("%sclient/%d/%d/%s", baseUrl, taskId, messageId, state);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .put(JordanInstance.EMPTY_BODY)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.code() == 202;
        }
    }

    public boolean received() throws IOException {
        return updateState(JordanConstants.MESSAGE_STATE_CLIENT_RECEIVED);
    }

    public boolean acknowledge() throws IOException {
        return updateState(JordanConstants.MESSAGE_STATE_ACKNOWLEDGED);
    }

    public boolean processed() throws IOException {
        return updateState(JordanConstants.MESSAGE_STATE_PROCESSED);
    }

    public boolean acknowledgeAndProcessed() throws IOException {
        boolean acked = acknowledge();
        return acked && processed();
    }

    public boolean cannotProcess() throws IOException {
        return updateState(JordanConstants.MESSAGE_STATE_ERROR_CANNOT_PROCESS);
    }

    public boolean overridden() throws IOException {
        return updateState(JordanConstants.MESSAGE_STATE_OVERRIDDEN);
    }

    public long getMessageId() { return messageId; }
    public String getActionName() { return actionName; }
    public Map<String, Object> getPlaceholders() { return Collections.unmodifiableMap(placeholders); }
    public Object getPlaceholder(String key) { return placeholders.get(key); }
}
