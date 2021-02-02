package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.ui.ClientDeletionCallback;
import com.mara.jordan.app.ui.FullDeletionCallback;
import com.mara.jordan.app.ui.GenericQueryCallback;

public class JordanClientModel implements JordanModel {

    private static final String TAG = "JordanServerModel";
    protected final Context context;
    private final JordanApi api;

    public JordanClientModel(Context ctx, String serverBaseUrl) {
        super();
        context = ctx.getApplicationContext();
        api = JordanApi.getInstance(context);
        if(serverBaseUrl != null){
            api.setServerBaseUrl(serverBaseUrl);
        }
    }

    protected JordanClientModel(Context ctx) {
        this(ctx, null);
    }

    public void listClients(JordanGetClientsCallback... callbacks) {
        api.listClients(callbacks);
    }

    public void delete(JordanClientDTO client, ClientDeletionCallback... callbacks) {
        api.deleteClient(client.getClientId(), callbacks);
    }

    public void genericQuery(String query, GenericQueryCallback... callbacks) {
        api.genericQuery(query, callbacks);
    }

    public void deleteAll(FullDeletionCallback... callbacks) {
        api.deleteAll(callbacks);
    }
}
