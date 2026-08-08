package com.mara.jordan.app.api;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.android.volley.ParseError;
import com.android.volley.VolleyError;
import com.mara.jordan.core.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.core.dto.JordanAdminCredentialsDTO;
import com.mara.jordan.core.dto.JordanAdminSessionDTO;
import com.mara.jordan.core.dto.JordanClientDTO;
import com.mara.jordan.core.dto.JordanMessageStateDTO;
import com.mara.jordan.core.dto.JordanSendMessageActionDTO;
import com.mara.jordan.core.dto.JordanSendMessageDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;
import com.mara.jordan.core.dto.JordanTestDTO;
import com.mara.jordan.app.R;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.ui.ClientDeletionCallback;
import com.mara.jordan.app.ui.FullDeletionCallback;
import com.mara.jordan.app.ui.GenericQueryCallback;
import com.mara.jordan.app.ui.ServerConnectionTestCallback;
import com.mara.jordan.app.utils.NetworkUtils;

import java.net.HttpURLConnection;
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

    /**
     * Screen to warn when the server refuses a call for lack of a valid admin session.
     * Registered by the visible fragment, cleared when it leaves.
     */
    @Setter
    private JordanAuthenticationListener authenticationListener;

    /**
     * Unregister a screen without stealing the place from the one that replaced it : while
     * navigating, the incoming fragment may register before the outgoing one leaves.
     */
    public void clearAuthenticationListener(JordanAuthenticationListener listener) {
        if (authenticationListener == listener) {
            authenticationListener = null;
        }
    }

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


    /**
     * Only a fallback : an authenticated server overrides the author with the operator
     * behind the token, and ignores what the request body claims.
     */
    private String getAuthor() {
        return Build.MODEL;
    }

    /**
     * Open an admin session on the current server. The token it returns is attached to every
     * following admin call, and the authenticated login becomes the {@code author} of the
     * messages sent from this device.
     */
    public void login(String login, String password, JordanLoginCallback... callbacks) {
        login(getServerBaseUrl(), login, password, callbacks);
    }

    public void login(String serverBaseUrl, String login, String password, JordanLoginCallback... callbacks) {
        String endpoint = "login";
        final String baseUrl = NetworkUtils.removeEndingSlash(serverBaseUrl);
        String url = String.format("%s/%s", baseUrl, endpoint);
        JordanAdminCredentialsDTO credentials = JordanAdminCredentialsDTO.builder()
                .login(login)
                .password(password)
                .build();
        GsonPostRequest<JordanAdminSessionDTO> loginRequest = new GsonPostRequest<>(
                url,
                credentials,
                JordanAdminSessionDTO.class,
                // no session yet : this endpoint is the one that creates it
                NetworkUtils.makeHeaders(),
                response -> handleLoginResponse(baseUrl, response, callbacks),
                error -> handleLoginError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(loginRequest);
    }

    private void handleLoginResponse(String serverBaseUrl, JordanAdminSessionDTO session, JordanLoginCallback... callbacks) {
        if (session == null || session.getToken() == null) {
            handleLoginError(new ParseError(new IllegalArgumentException("Server returned no session token")), callbacks);
            return;
        }
        JordanSession.getInstance().open(serverBaseUrl, session);
        Log.i(TAG, "Admin session opened for " + session.getLogin() + " (" + session.getRole() + ")");
        for (JordanLoginCallback callback : callbacks) {
            callback.onLoggedIn(session);
        }
    }

    private void handleLoginError(VolleyError error, JordanLoginCallback[] callbacks) {
        // a 401 here means wrong credentials, not a missing session : reporting it to the
        // authentication listener would re-open the login dialog in a loop
        final String message = isUnauthorized(error)
                ? context.getString(R.string.login_failure_credentials)
                : extractErrorMessage(error);
        for (JordanLoginCallback callback : callbacks) {
            callback.onLoginError(message);
        }
    }

    /**
     * Close the session opened on the current server, both here and on the server side.
     */
    public void logout(JordanLogoutCallback... callbacks) {
        String endpoint = "logout";
        final String baseUrl = NetworkUtils.removeEndingSlash(getServerBaseUrl());
        String url = String.format("%s/%s", baseUrl, endpoint);
        GsonPostRequest<String> logoutRequest = new GsonPostRequest<>(
                url,
                null,
                String.class,
                NetworkUtils.makeHeaders(baseUrl),
                response -> handleLogoutResponse(baseUrl, callbacks),
                error -> handleLogoutError(baseUrl, error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(logoutRequest);
    }

    private void handleLogoutResponse(String serverBaseUrl, JordanLogoutCallback... callbacks) {
        JordanSession.getInstance().close(serverBaseUrl);
        for (JordanLogoutCallback callback : callbacks) {
            callback.onLoggedOut();
        }
    }

    private void handleLogoutError(String serverBaseUrl, VolleyError error, JordanLogoutCallback[] callbacks) {
        // the token is dropped locally whatever the server answered : keeping it would only
        // let the next screen believe it is still authenticated
        JordanSession.getInstance().close(serverBaseUrl);
        if (isUnauthorized(error)) {
            // the session was already gone server-side, nothing more to close
            handleLogoutResponse(serverBaseUrl, callbacks);
            return;
        }
        for (JordanLogoutCallback callback : callbacks) {
            callback.onLogoutError(extractErrorMessage(error));
        }
    }

    public boolean isAuthenticated() {
        return JordanSession.getInstance().hasSession(getServerBaseUrl());
    }

    public JordanAdminSessionDTO getCurrentSession() {
        return JordanSession.getInstance().get(getServerBaseUrl());
    }

    public void readStatus(long taskId, int lineCount, JordanReadStatusCallback... callbacks) {
        String endpoint = "status";
        String url = String.format("%s/%d/%s/%s", getServerBaseUrl(), taskId, endpoint, lineCount);
        GsonGetRequest<JordanStatusDTO[]> readStatusRequest = new GsonGetRequest<>(
                url,
                JordanStatusDTO[].class,
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readStatusRequest);
    }

    private void handleError(VolleyError error, JordanReadStatusCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(JordanReadStatusCallback callback : callbacks){
            callback.onStatusLoadingError(message);
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
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readMessagesRequest);
    }


    private void handleError(VolleyError error, JordanReadMessagesCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(JordanReadMessagesCallback callback : callbacks){
            callback.onMessagesLoadingError(message);
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
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readActionsRequest);
    }

    private void handleError(VolleyError error, JordanGetActionsCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(JordanGetActionsCallback callback : callbacks){
            callback.onActionsLoadingError(message);
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
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(sendMessageRequest);
    }

    private void handleError(VolleyError error, JordanSendMessageCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(JordanSendMessageCallback callback : callbacks){
            callback.onMessageSendingError(message);
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

    /**
     * List the clients of a server other than the current one, as the server list screen does
     * for every known server : the session used is the one opened on that very server.
     */
    public void listClients(JordanServer server, JordanGetClientsCallback... callbacks) {
        listClients(server.getUrl(), callbacks);
    }

    public void listClients(String serverBaseUrl, JordanGetClientsCallback... callbacks) {
        String endpoint = "clients";
        String url = String.format("%s/%s", serverBaseUrl, endpoint);
        GsonGetRequest<JordanClientDTO[]> readClientsRequest = new GsonGetRequest<>(
                url,
                JordanClientDTO[].class,
                NetworkUtils.makeHeaders(serverBaseUrl),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, serverBaseUrl, callbacks)
        );
        Log.i(TAG, "Queuing " + endpoint + " query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readClientsRequest);
    }

    private void handleError(VolleyError error, String targetBaseUrl, JordanGetClientsCallback[] callbacks) {
        final String message = onRequestFailed(error, targetBaseUrl);
        for(JordanGetClientsCallback callback : callbacks){
            callback.onClientsLoadingError(message);
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
                NetworkUtils.makeHeaders(serverBaseUrl),
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
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing DELETE query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(deleteClientRequest);
    }

    private void handleError(VolleyError error, ClientDeletionCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(ClientDeletionCallback callback : callbacks){
            callback.onClientDeletionError(message);
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
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing Generic query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(readClientsRequest);
    }


    private void handleError(VolleyError error, GenericQueryCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(GenericQueryCallback callback : callbacks){
            callback.onGenericQueryError(message);
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
                NetworkUtils.makeHeaders(getServerBaseUrl()),
                response -> handleResponse(response, callbacks),
                error -> handleError(error, callbacks)
        );
        Log.i(TAG, "Queuing DELETE query : " + url);
        VolleyInterfaceSingleton.getInstance(context).addToRequestQueue(deleteAllRequest);
    }

    private void handleError(VolleyError error, FullDeletionCallback[] callbacks) {
        final String message = onRequestFailed(error);
        for(FullDeletionCallback callback : callbacks){
            callback.onBaseDeletionError(message);
        }
    }

    private void handleResponse(String response, FullDeletionCallback... callbacks) {
        for (FullDeletionCallback callback : callbacks) {
            callback.onBaseDeleted();
        }
    }
    private String onRequestFailed(VolleyError error) {
        return onRequestFailed(error, getServerBaseUrl());
    }

    /**
     * Common branch of every failing admin call. A 401 means this device holds no valid session
     * (never opened, expired, or revoked server-side) : the stale token is dropped and the visible
     * screen is asked for credentials, instead of reporting a network error nobody can act on.
     * A 403 means the session is valid but the operator role is too narrow, which no login fixes.
     *
     * @param targetBaseUrl server the failing call was addressed to, which is not always the
     *                      current one : the server list screen queries them all
     */
    private String onRequestFailed(VolleyError error, String targetBaseUrl) {
        if (isForbidden(error)) {
            return context.getString(R.string.error_permission_denied);
        }
        if (!isUnauthorized(error)) {
            return extractErrorMessage(error);
        }
        Log.w(TAG, "Server " + targetBaseUrl + " refused the call with 401, an admin session is required");
        JordanSession.getInstance().close(targetBaseUrl);
        if (authenticationListener != null) {
            authenticationListener.onAuthenticationRequired(targetBaseUrl);
        }
        return context.getString(R.string.error_authentication_required);
    }

    private static boolean isUnauthorized(VolleyError error) {
        return hasStatusCode(error, HttpURLConnection.HTTP_UNAUTHORIZED);
    }

    private static boolean isForbidden(VolleyError error) {
        return hasStatusCode(error, HttpURLConnection.HTTP_FORBIDDEN);
    }

    private static boolean hasStatusCode(VolleyError error, int statusCode) {
        return error != null && error.networkResponse != null && error.networkResponse.statusCode == statusCode;
    }

    private static String extractErrorMessage(VolleyError error) {
        return String.format("%s %s", error.toString(), error.getMessage());
    }
}
