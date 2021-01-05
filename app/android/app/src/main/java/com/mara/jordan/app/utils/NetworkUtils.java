package com.mara.jordan.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.HashMap;
import java.util.Map;

public class NetworkUtils {
    public static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static Map<String, String> makeHeaders() {
        return new HashMap<>();
    }
}
