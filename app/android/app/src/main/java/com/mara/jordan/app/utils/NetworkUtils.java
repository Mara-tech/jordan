package com.mara.jordan.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

public class NetworkUtils {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_TYPE_JSON = "application/json";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static Map<String, String> makeHeaders() {
        return ImmutableMap.of(
                HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_JSON
                //HEADER_AUTHORIZATION, "Bearer <token>"
        );
    }
}
