package com.mara.jordan.app.utils;

import com.mara.jordan.app.api.JordanSession;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.core.dto.JordanAdminSessionDTO;

import org.junit.After;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class NetworkUtilsTest {

    private static final String SERVER_URL = "https://example.com/jordan/admin";

    @After
    public void closeSessions() {
        JordanSession.getInstance().close(SERVER_URL);
    }

    @Test
    public void removeEndingSlash_withTrailingSlash_removesIt() {
        assertEquals("https://example.com/jordan", NetworkUtils.removeEndingSlash("https://example.com/jordan/"));
    }

    @Test
    public void removeEndingSlash_withoutTrailingSlash_unchanged() {
        assertEquals("https://example.com/jordan", NetworkUtils.removeEndingSlash("https://example.com/jordan"));
    }

    @Test
    public void removeEndingSlash_withDoubleTrailingSlash_removesOnlyLast() {
        assertEquals("https://example.com/jordan/", NetworkUtils.removeEndingSlash("https://example.com/jordan//"));
    }

    @Test
    public void removeEndingSlash_emptyString_unchanged() {
        assertEquals("", NetworkUtils.removeEndingSlash(""));
    }

    @Test
    public void removeEndingSlash_slashOnly_returnsEmpty() {
        assertEquals("", NetworkUtils.removeEndingSlash("/"));
    }

    @Test
    public void removeEndingSlash_null_returnsNull() {
        assertNull(NetworkUtils.removeEndingSlash(null));
    }

    @Test
    public void makeHeaders_withoutSession_sendsJsonOnly() {
        Map<String, String> headers = NetworkUtils.makeHeaders(SERVER_URL);

        assertEquals(NetworkUtils.HEADER_CONTENT_TYPE_JSON, headers.get(NetworkUtils.HEADER_CONTENT_TYPE));
        assertFalse(headers.containsKey(NetworkUtils.HEADER_AUTHORIZATION));
    }

    @Test
    public void makeHeaders_withOpenSession_sendsBearerToken() {
        openSession(SERVER_URL, "abcdef", expiresIn(3600));

        Map<String, String> headers = NetworkUtils.makeHeaders(SERVER_URL);

        assertEquals("Bearer abcdef", headers.get(NetworkUtils.HEADER_AUTHORIZATION));
    }

    @Test
    public void makeHeaders_withTrailingSlash_findsTheSameSession() {
        openSession(SERVER_URL, "abcdef", expiresIn(3600));

        Map<String, String> headers = NetworkUtils.makeHeaders(SERVER_URL + "/");

        assertEquals("Bearer abcdef", headers.get(NetworkUtils.HEADER_AUTHORIZATION));
    }

    @Test
    public void makeHeaders_withExpiredSession_sendsNoToken() {
        openSession(SERVER_URL, "abcdef", expiresIn(-1));

        Map<String, String> headers = NetworkUtils.makeHeaders(SERVER_URL);

        assertFalse(headers.containsKey(NetworkUtils.HEADER_AUTHORIZATION));
    }

    @Test
    public void makeHeaders_ofAnotherServer_sendsNoToken() {
        openSession(SERVER_URL, "abcdef", expiresIn(3600));

        Map<String, String> headers = NetworkUtils.makeHeaders("https://other.example.com/jordan/admin");

        assertFalse(headers.containsKey(NetworkUtils.HEADER_AUTHORIZATION));
    }

    @Test
    public void makeHeaders_fromServerEntity_sendsItsOwnToken() {
        openSession(SERVER_URL, "abcdef", expiresIn(3600));

        Map<String, String> headers = NetworkUtils.makeHeaders(
                JordanServer.builder().name("prod").url(SERVER_URL).build());

        assertEquals("Bearer abcdef", headers.get(NetworkUtils.HEADER_AUTHORIZATION));
    }

    @Test
    public void makeHeaders_withoutServer_sendsJsonOnly() {
        Map<String, String> headers = NetworkUtils.makeHeaders((JordanServer) null);

        assertEquals(NetworkUtils.HEADER_CONTENT_TYPE_JSON, headers.get(NetworkUtils.HEADER_CONTENT_TYPE));
        assertFalse(headers.containsKey(NetworkUtils.HEADER_AUTHORIZATION));
    }

    private static void openSession(String serverBaseUrl, String token, long expiresAt) {
        JordanSession.getInstance().open(serverBaseUrl, JordanAdminSessionDTO.builder()
                .login("alice")
                .role("operator")
                .token(token)
                .expiresAt(expiresAt)
                .build());
    }

    private static long expiresIn(long seconds) {
        return System.currentTimeMillis() / 1000L + seconds;
    }
}
