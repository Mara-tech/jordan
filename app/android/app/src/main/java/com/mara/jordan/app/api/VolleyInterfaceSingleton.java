package com.mara.jordan.app.api;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class VolleyInterfaceSingleton {
    private static VolleyInterfaceSingleton instance;
    private final Context context;
    private RequestQueue queue;

    private VolleyInterfaceSingleton(Context context) {
        this.context = context;
    }

    public static synchronized VolleyInterfaceSingleton getInstance(Context context) {
        if (instance == null) {
            instance = new VolleyInterfaceSingleton(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (queue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            queue = Volley.newRequestQueue(context.getApplicationContext()/*, getHttpStack()*/);
        }
        return queue;
    }

    public void addToRequestQueue(Request<?> request) {
        getRequestQueue().add(request);
    }
}
