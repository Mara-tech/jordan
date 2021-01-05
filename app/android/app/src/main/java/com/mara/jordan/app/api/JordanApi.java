package com.mara.jordan.app.api;

import android.content.Context;
import android.util.Log;

import com.android.volley.VolleyError;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;
import com.mara.jordan.app.utils.NetworkUtils;

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

    public void readStatus(JordanReadStatusCallback... callbacks) {
        String taskId = getTaskId();
        String endpoint = "status";
        String lineCount = "10";
        String url = String.format("%s/%s/%s/%s", getServerBaseUrl(), taskId, endpoint, lineCount);
        GsonRequest<JordanStatusDTO[]> readStatusRequest = new GsonRequest<>(
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
        GsonRequest<JordanMessageStateDTO[]> readStatusRequest = new GsonRequest<>(
                url,
                JordanMessageStateDTO[].class,
                NetworkUtils.makeHeaders(),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readStatusRequest);
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

    private static String extractErrorMessage(VolleyError error) {
        return String.format("%s %s", error.toString(), error.getMessage());
    }
}
