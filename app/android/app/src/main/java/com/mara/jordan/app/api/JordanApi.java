package com.mara.jordan.app.api;

import android.content.Context;
import android.util.Log;

import com.android.volley.VolleyError;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.model.dto.JordanSendMessageActionDTO;
import com.mara.jordan.app.model.dto.JordanSendMessageDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;
import com.mara.jordan.app.utils.NetworkUtils;

import java.util.Map;

public class JordanApi {

    private static final String TAG = "JordanApi";
    private final Context context;

    public JordanApi(Context context) {
        this.context = context;
    }


    private String getServerBaseUrl() {
        return context.getString(R.string.default_server_base_uri);
    }

    private String getTaskId() {
        return "123";
    }

    private String getAuthor() {
        return "pbaudet";
    }

    public void readStatus(JordanReadStatusCallback... callbacks) {
        String taskId = getTaskId();
        String endpoint = "status";
        String lineCount = "10";
        String url = String.format("%s/%s/%s/%s", getServerBaseUrl(), taskId, endpoint, lineCount);
        GsonGetRequest<JordanStatusDTO[]> readStatusRequest = new GsonGetRequest<>(
                url,
                JordanStatusDTO[].class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readStatusRequest);
    }

    private void handleError(VolleyError error, JordanReadStatusCallback[] callbacks) {
        for(JordanReadStatusCallback callback :callbacks){
            callback.onStatusLoadingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(JordanStatusDTO[] response, JordanReadStatusCallback... callbacks) {
        final JordanStatusDTO[] safeResponse = response != null ? response : new JordanStatusDTO[]{};
        for(JordanReadStatusCallback callback : callbacks){
            callback.onStatusLoaded(safeResponse);
        }
    }

    public void readMessages(JordanReadMessagesCallback... callbacks) {
        String taskId = getTaskId();
        String endpoint = "messages";
        String url = String.format("%s/%s/%s", getServerBaseUrl(), taskId, endpoint);
        GsonGetRequest<JordanMessageStateDTO[]> readMessagesRequest = new GsonGetRequest<>(
                url,
                JordanMessageStateDTO[].class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readMessagesRequest);
    }


    private void handleError(VolleyError error, JordanReadMessagesCallback[] callbacks) {
        for(JordanReadMessagesCallback callback :callbacks){
            callback.onMessagesLoadingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(JordanMessageStateDTO[] response, JordanReadMessagesCallback... callbacks) {
        final JordanMessageStateDTO[] safeResponse = response != null ? response : new JordanMessageStateDTO[]{};
        for(JordanReadMessagesCallback callback : callbacks){
            callback.onMessagesLoaded(safeResponse);
        }
    }

    public void readActionDefinitions(JordanGetActionsCallback... callbacks) {
        String taskId = getTaskId();
        String endpoint = "actions";
        String url = String.format("%s/%s/%s", getServerBaseUrl(), taskId, endpoint);
        GsonGetRequest<JordanActionDefinitionWithTaskDTO[]> readActionsRequest = new GsonGetRequest<>(
                url,
                JordanActionDefinitionWithTaskDTO[].class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readActionsRequest);
    }

    private void handleError(VolleyError error, JordanGetActionsCallback[] callbacks) {
        for(JordanGetActionsCallback callback :callbacks){
            callback.onActionsLoadingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(JordanActionDefinitionWithTaskDTO[] response, JordanGetActionsCallback... callbacks) {
        final JordanActionDefinitionWithTaskDTO[] safeResponse = response != null ? response : new JordanActionDefinitionWithTaskDTO[]{};
        for(JordanGetActionsCallback callback : callbacks){
            callback.onActionsLoaded(safeResponse);
        }
    }

    public void sendMessage(long taskId, String actionName, Map<String, Object> placeholders, JordanSendMessageCallback... callbacks) {
        String endpoint = "message";
        String url = String.format("%s/%s/%s", getServerBaseUrl(), taskId, endpoint);
        JordanSendMessageDTO requestDTO = JordanSendMessageDTO.builder()
                .author(getAuthor())
                .action(JordanSendMessageActionDTO.builder()
                        .actionName(actionName)
                        .placeholders(placeholders)
                        .build())
                .build();
        GsonPostRequest<Long> sendMessageRequest = new GsonPostRequest<>(
                url,
                requestDTO,
                Long.class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(sendMessageRequest);
    }

    private void handleError(VolleyError error, JordanSendMessageCallback[] callbacks) {
        for(JordanSendMessageCallback callback :callbacks){
            callback.onMessageSendingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(Long response, JordanSendMessageCallback... callbacks) {
        final long safeResponse = response != null ? response : -1L;
        for(JordanSendMessageCallback callback : callbacks){
            callback.onMessageSent(safeResponse);
        }
    }
    private static String extractErrorMessage(VolleyError error) {
        return String.format("%s %s", error.toString(), error.getMessage());
    }
}
