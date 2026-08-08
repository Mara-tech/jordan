package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanAuthenticationListener;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.api.JordanLoginCallback;
import com.mara.jordan.app.api.JordanLogoutCallback;
import com.mara.jordan.core.dto.JordanAdminSessionDTO;
import com.mara.jordan.core.dto.JordanClientDTO;
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

    /**
     * A model on the server currently in use, without changing it.
     */
    public JordanClientModel(Context ctx) {
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

    public String getServerBaseUrl() {
        return api.getServerBaseUrl();
    }

    public void login(String login, String password, JordanLoginCallback... callbacks) {
        api.login(login, password, callbacks);
    }

    public void logout(JordanLogoutCallback... callbacks) {
        api.logout(callbacks);
    }

    public boolean isAuthenticated() {
        return api.isAuthenticated();
    }

    public JordanAdminSessionDTO getCurrentSession() {
        return api.getCurrentSession();
    }

    /**
     * The screen in charge of asking for credentials when the server answers 401.
     */
    public void setAuthenticationListener(JordanAuthenticationListener listener) {
        api.setAuthenticationListener(listener);
    }

    public void clearAuthenticationListener(JordanAuthenticationListener listener) {
        api.clearAuthenticationListener(listener);
    }
}
