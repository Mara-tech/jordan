package com.mara.jordan.app.api;

import com.android.volley.Response;

import java.util.Map;

public class GsonDeletetRequest<T> extends AGsonRequest<T> {
    private static final String TAG = "GsonDeleteRequest";

    /**
     * Make a POST request and return a parsed object from JSON.
     *
     * @param url URL of the request to make
     * @param clazz Relevant class object, for Gson's reflection for response
     * @param headers Map of request headers
     */
    public GsonDeletetRequest(String url, Class<T> clazz, Map<String, String> headers,
                              Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(Method.DELETE, url, clazz, headers, listener, errorListener);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
