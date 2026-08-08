package com.mara.jordan.app.utils;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableMap;
import com.mara.jordan.app.api.JordanSession;
import com.mara.jordan.app.db.JordanServer;

import java.util.Map;

public class NetworkUtils {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_TYPE_JSON = "application/json";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * Headers for the endpoints left open by the server ({@code /hello}, {@code /admin/login}).
     */
    public static Map<String, String> makeHeaders() {
        return ImmutableMap.of(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_JSON);
    }

    /**
     * Headers for an {@code /jordan/admin/*} call : the session token opened for this server,
     * when there is one. Without it the server answers 401, which the UI turns into a login prompt.
     */
    public static Map<String, String> makeHeaders(@Nullable JordanServer server) {
        return makeHeaders(server != null ? server.getUrl() : null);
    }

    public static Map<String, String> makeHeaders(@Nullable String serverBaseUrl) {
        final String token = JordanSession.getInstance().tokenFor(serverBaseUrl);
        if (token == null || token.isEmpty()) {
            return makeHeaders();
        }
        return ImmutableMap.of(
                HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_JSON,
                HEADER_AUTHORIZATION, BEARER_PREFIX + token
        );
    }

    /**
     * e.g https://example.com/jordan/admin/ returns https://example.com/jordan/admin
     */
    public static String removeEndingSlash(String baseUrl) {
        String SLASH = "/";
        if(baseUrl == null){
            return null;
        }
        if(baseUrl.endsWith(SLASH)){
            return baseUrl.substring(0, baseUrl.length() -1);
        }
        return baseUrl;
    }
}
