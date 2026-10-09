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

    /**
     * @param data the message as read, decoded from JSON
     * @throws IllegalArgumentException when {@code data} is not a message: {@code messageId}, {@code action} or
     *         {@code action.actionName} missing or of the wrong type, {@code action.placeholders} not an object
     */
    @SuppressWarnings("unchecked")
    JordanMessage(String baseUrl, long taskId, String authToken, Map data, OkHttpClient httpClient, Gson gson) {
        this.baseUrl = baseUrl;
        this.taskId = taskId;
        this.authToken = authToken;
        this.httpClient = httpClient;
        if (data == null) {
            throw new IllegalArgumentException("the message is not a JSON object");
        }
        this.messageId = field(data, "messageId", Number.class).longValue();
        Map action = field(data, "action", Map.class);
        this.actionName = field(action, "actionName", String.class);
        // optional in the contract: an action without them is an action without parameters
        Object ph = action.get("placeholders");
        if (ph != null && !(ph instanceof Map)) {
            throw new IllegalArgumentException("'placeholders' is not an object: " + ph);
        }
        this.placeholders = ph != null ? (Map<String, Object>) ph : Collections.<String, Object>emptyMap();
    }

    private static <T> T field(Map data, String name, Class<T> type) {
        Object value = data.get(name);
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(value == null
                    ? "'" + name + "' is missing"
                    : "'" + name + "' is not a " + type.getSimpleName() + ": " + value);
        }
        return type.cast(value);
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
