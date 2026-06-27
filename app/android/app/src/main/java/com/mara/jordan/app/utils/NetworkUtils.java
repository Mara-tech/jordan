package com.mara.jordan.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class NetworkUtils {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_TYPE_JSON = "application/json";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network activeNetwork = connMgr.getActiveNetwork();
        if (activeNetwork == null) return false;
        final NetworkCapabilities capabilities = connMgr.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public static Map<String, String> makeHeaders() {
        return ImmutableMap.of(
                HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_JSON
                //HEADER_AUTHORIZATION, "Bearer <token>"
        );
    }

    /**
     * e.g https://example.com/jordan/admin/ returns https://example.com/jordan/admin
     */
    public static String removeEndingSlash(String baseUrl) {
        String SLASH = "/";
        if(baseUrl.endsWith(SLASH)){
            return baseUrl.substring(0, baseUrl.length() -1);
        }
        return baseUrl;
    }
}
