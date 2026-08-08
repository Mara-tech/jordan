package com.mara.jordan.app.api;

import androidx.annotation.Nullable;

import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.utils.NetworkUtils;
import com.mara.jordan.core.dto.JordanAdminSessionDTO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin sessions opened through {@code POST /jordan/admin/login}, held in memory only :
 * a session token is a short-lived secret and has no reason to survive the process.
 * <p>
 * Sessions are keyed by server base URL because the server list screen queries every
 * known server at once, and each server issues its own token.
 */
public final class JordanSession {

    private static final JordanSession INSTANCE = new JordanSession();

    private final Map<String, JordanAdminSessionDTO> sessionsByBaseUrl = new ConcurrentHashMap<>();

    private JordanSession() {
    }

    public static JordanSession getInstance() {
        return INSTANCE;
    }

    public void open(String serverBaseUrl, JordanAdminSessionDTO session) {
        if (serverBaseUrl != null && session != null && session.getToken() != null) {
            sessionsByBaseUrl.put(key(serverBaseUrl), session);
        }
    }

    public void close(@Nullable String serverBaseUrl) {
        if (serverBaseUrl != null) {
            sessionsByBaseUrl.remove(key(serverBaseUrl));
        }
    }

    /**
     * @return the session opened for this server, or {@code null} when there is none left :
     * an expired session is dropped here rather than sent and refused with a 401.
     */
    @Nullable
    public JordanAdminSessionDTO get(@Nullable String serverBaseUrl) {
        if (serverBaseUrl == null) {
            return null;
        }
        final String key = key(serverBaseUrl);
        final JordanAdminSessionDTO session = sessionsByBaseUrl.get(key);
        if (session == null) {
            return null;
        }
        if (isExpired(session)) {
            sessionsByBaseUrl.remove(key);
            return null;
        }
        return session;
    }

    @Nullable
    public String tokenFor(@Nullable String serverBaseUrl) {
        final JordanAdminSessionDTO session = get(serverBaseUrl);
        return session != null ? session.getToken() : null;
    }

    @Nullable
    public String tokenFor(@Nullable JordanServer server) {
        return server != null ? tokenFor(server.getUrl()) : null;
    }

    public boolean hasSession(@Nullable String serverBaseUrl) {
        return get(serverBaseUrl) != null;
    }

    public boolean hasSession(@Nullable JordanServer server) {
        return server != null && hasSession(server.getUrl());
    }

    private static boolean isExpired(JordanAdminSessionDTO session) {
        // expiresAt is in seconds since 1970/1/1, as everywhere else in the protocol
        return session.getExpiresAt() > 0 && session.getExpiresAt() <= System.currentTimeMillis() / 1000L;
    }

    /**
     * The same server reached with and without a trailing slash is the same server.
     */
    private static String key(String serverBaseUrl) {
        return NetworkUtils.removeEndingSlash(serverBaseUrl);
    }
}
