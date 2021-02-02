package com.mara.jordan.app.api;

import android.content.Context;
import android.util.Log;

import com.android.volley.ParseError;
import com.android.volley.VolleyError;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.model.dto.JordanSendMessageActionDTO;
import com.mara.jordan.app.model.dto.JordanSendMessageDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;
import com.mara.jordan.app.model.dto.JordanTestDTO;
import com.mara.jordan.app.ui.ClientDeletionCallback;
import com.mara.jordan.app.ui.FullDeletionCallback;
import com.mara.jordan.app.ui.GenericQueryCallback;
import com.mara.jordan.app.ui.ServerConnectionTestCallback;
import com.mara.jordan.app.utils.NetworkUtils;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

public class JordanApi {

    private static final String TAG = "JordanApi";
    private static JordanApi instance;
    private final Context context;

    @Getter
    @Setter
    private String serverBaseUrl;

    private JordanApi(Context context) {
        super();
        this.context = context.getApplicationContext();
    }

    public static synchronized JordanApi getInstance(Context ctx) {
        if(instance == null){
            instance = new JordanApi(ctx);
        }
        return instance;
    }


    private String getAuthor() {
        return "pbaudet";
    }

    public void readStatus(long taskId, int lineCount, JordanReadStatusCallback... callbacks) {
        String endpoint = "status";
        String url = String.format("%s/%d/%s/%s", getServerBaseUrl(), taskId, endpoint, lineCount);
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
        for(JordanReadStatusCallback callback : callbacks){
            callback.onStatusLoadingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(JordanStatusDTO[] response, JordanReadStatusCallback... callbacks) {
        final JordanStatusDTO[] safeResponse = response != null ? response : new JordanStatusDTO[]{};
        for(JordanReadStatusCallback callback : callbacks){
            callback.onStatusLoaded(safeResponse);
        }
    }

    public void readMessages(long taskId,JordanReadMessagesCallback... callbacks) {
        String endpoint = "messages";
        String url = String.format("%s/%d/%s", getServerBaseUrl(), taskId, endpoint);
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
        for(JordanReadMessagesCallback callback : callbacks){
            callback.onMessagesLoadingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(JordanMessageStateDTO[] response, JordanReadMessagesCallback... callbacks) {
        final JordanMessageStateDTO[] safeResponse = response != null ? response : new JordanMessageStateDTO[]{};
        for(JordanReadMessagesCallback callback : callbacks){
            callback.onMessagesLoaded(safeResponse);
        }
    }

    public void readActionDefinitions(long taskId, JordanGetActionsCallback... callbacks) {
        String endpoint = "actions";
        String url = String.format("%s/%d/%s", getServerBaseUrl(), taskId, endpoint);
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
        for(JordanGetActionsCallback callback : callbacks){
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
        for(JordanSendMessageCallback callback : callbacks){
            callback.onMessageSendingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(Long response, JordanSendMessageCallback... callbacks) {
        final long safeResponse = response != null ? response : -1L;
        for(JordanSendMessageCallback callback : callbacks){
            callback.onMessageSent(safeResponse);
        }
    }

    public void listClients(JordanGetClientsCallback... callbacks) {
        listClients(getServerBaseUrl(), callbacks);
    }

    public void listClients(String serverBaseUrl, JordanGetClientsCallback... callbacks) {
        String endpoint = "clients";
        String url = String.format("%s/%s", serverBaseUrl, endpoint);
        GsonGetRequest<JordanClientDTO[]> readClientsRequest = new GsonGetRequest<>(
                url,
                JordanClientDTO[].class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readClientsRequest);
    }

    private void handleError(VolleyError error, JordanGetClientsCallback[] callbacks) {
        for(JordanGetClientsCallback callback : callbacks){
            callback.onClientsLoadingError(extractErrorMessage(error));
        }
    }

    private void handleResponse(JordanClientDTO[] response, JordanGetClientsCallback... callbacks) {
        final JordanClientDTO[] safeResponse = response != null ? response : new JordanClientDTO[]{};
        for(JordanGetClientsCallback callback : callbacks){
            callback.onClientsLoaded(safeResponse);
        }
    }

    public void testConnection(String serverBaseUrl, ServerConnectionTestCallback... callbacks) {
        String endpoint = "hello";
        String url = String.format("%s/%s", NetworkUtils.removeEndingSlash(serverBaseUrl), endpoint);
        GsonGetRequest<JordanTestDTO> testConnectionRequest = new GsonGetRequest<>(
                url,
                JordanTestDTO.class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(testConnectionRequest);
    }

    private void handleError(VolleyError error, ServerConnectionTestCallback[] callbacks) {
        for(ServerConnectionTestCallback callback : callbacks){
            callback.onConnectionTestError(error);
        }
    }

    private void handleResponse(JordanTestDTO response, ServerConnectionTestCallback... callbacks) {
        if(response == null){
            handleError(new ParseError(new IllegalArgumentException("Response should not be null.")), callbacks);
        } else {
            for (ServerConnectionTestCallback callback : callbacks) {
                callback.onConnectionTestPassed(response);
            }
        }
    }

    public void deleteClient(long clientId, ClientDeletionCallback... callbacks) {
        String url = String.format("%s/%s", NetworkUtils.removeEndingSlash(serverBaseUrl), clientId);
        GsonDeletetRequest<String> deleteClientRequest = new GsonDeletetRequest<>(
                url,
                String.class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing DELETE query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(deleteClientRequest);
    }

    private void handleError(VolleyError error, ClientDeletionCallback[] callbacks) {
        for(ClientDeletionCallback callback : callbacks){
            callback.onClientDeletionError(extractErrorMessage(error));
        }
    }

    private void handleResponse(String response, ClientDeletionCallback... callbacks) {
        for (ClientDeletionCallback callback : callbacks) {
            callback.onClientDeleted();
        }
    }

    public void genericQuery(String query, GenericQueryCallback... callbacks) {
        String url = String.format("%s/%s", NetworkUtils.removeEndingSlash(serverBaseUrl), query);
        StringRequest readClientsRequest = new StringRequest(
                url,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing Generic query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readClientsRequest);
    }


    private void handleError(VolleyError error, GenericQueryCallback[] callbacks) {
        for(GenericQueryCallback callback : callbacks){
            callback.onGenericQueryError(extractErrorMessage(error));
        }
    }

    private void handleResponse(String response, GenericQueryCallback... callbacks) {
        for (GenericQueryCallback callback : callbacks) {
            callback.onGenericQueryResponse(response);
        }
    }

    public void deleteAll(FullDeletionCallback... callbacks) {
        String endpoint = "all";
        String url = String.format("%s/%s", NetworkUtils.removeEndingSlash(serverBaseUrl), endpoint);
        GsonDeletetRequest<String> deleteAllRequest = new GsonDeletetRequest<>(
                url,
                String.class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing DELETE query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(deleteAllRequest);
    }

    private void handleError(VolleyError error, FullDeletionCallback[] callbacks) {
        for(FullDeletionCallback callback : callbacks){
            callback.onBaseDeletionError(extractErrorMessage(error));
        }
    }

    private void handleResponse(String response, FullDeletionCallback... callbacks) {
        for (FullDeletionCallback callback : callbacks) {
            callback.onBaseDeleted();
        }
    }
    private static String extractErrorMessage(VolleyError error) {
        return String.format("%s %s", error.toString(), error.getMessage());
    }
}
