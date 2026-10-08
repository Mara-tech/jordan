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
    // whether the server confirmed CLIENT_RECEIVED, and the error of the last attempt if it raised one —
    // both written by received() alone
    private boolean receiptConfirmed;
    private IOException receiptError;

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

    /**
     * Tells the server the message reached the program, at best effort: an {@link IOException} is kept in
     * {@link #getReceiptError()} rather than thrown, since the message is already in the program's hands —
     * the server took it off the queue when it answered the read (JRD-27). {@link JordanInstance#readMessage()}
     * sends it; calling it again retries one that was not confirmed.
     *
     * <p>After a timeout the outcome is unknown, not negative: the server may have recorded the receipt and only
     * its answer was lost — a retry then records it twice in the message's history.
     *
     * @return {@link #isReceiptConfirmed()}
     */
    public boolean received() {
        receiptError = null;
        try {
            receiptConfirmed = updateState(JordanConstants.MESSAGE_STATE_CLIENT_RECEIVED);
        } catch (IOException e) {
            receiptConfirmed = false;
            receiptError = e;
        }
        return receiptConfirmed;
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
    /** Whether the server answered the last {@link #received()} with 202. */
    public boolean isReceiptConfirmed() { return receiptConfirmed; }
    /** The error the last {@link #received()} raised, or null when it got an answer — 202 or not. */
    public IOException getReceiptError() { return receiptError; }
}
