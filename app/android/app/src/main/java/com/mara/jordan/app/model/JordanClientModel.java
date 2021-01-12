package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanGetClientsCallback;

public class JordanClientModel implements JordanModel {


    private static final String TAG = "JordanServerModel";
    private final Context context;
    private final JordanApi api;

    public JordanClientModel(Context ctx, String serverBaseUrl) {
        context = ctx;

        api = JordanApi.getInstance(context);
        api.setServerBaseUrl(serverBaseUrl);
    }

    public void listClients(JordanGetClientsCallback... callbacks) {
        api.listClients(callbacks);
    }
}
