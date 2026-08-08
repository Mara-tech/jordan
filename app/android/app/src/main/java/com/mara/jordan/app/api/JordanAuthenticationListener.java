package com.mara.jordan.app.api;

/**
 * Notified when the server answers 401 to an admin call : the screen showing the data
 * is expected to ask for credentials instead of reporting a network failure.
 */
public interface JordanAuthenticationListener {

    /**
     * @param serverBaseUrl base URL of the server that refused the call
     */
    void onAuthenticationRequired(String serverBaseUrl);
}
