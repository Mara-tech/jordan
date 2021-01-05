package com.mara.jordan.app.api;

import android.content.Context;
import android.util.Log;

import com.android.volley.VolleyError;
import com.mara.jordan.app.R;
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

    public void readStatus(JordanReadStatusCallback... callbacks) {
        String taskId = "123";
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

    private static String extractErrorMessage(VolleyError error) {
        return String.format("%s %s", error.toString(), error.getMessage());
    }
}
