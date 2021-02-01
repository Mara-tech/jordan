package com.mara.jordan.app.api;

import com.android.volley.Response;

import java.util.Map;

/**
 * https://developer.android.com/training/volley/request-custom#example:-gsonrequest
 */
public class GsonGetRequest<T> extends AGsonRequest<T> {
    private static final String TAG = "GsonGetRequest";

    private static final int TIMEOUT_MS = 5000;
    private static final int RETRIES = 2;

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param url URL of the request to make
     * @param clazz Relevant class object, for Gson's reflection
     * @param headers Map of request headers
     */
    public GsonGetRequest(String url, Class<T> clazz, Map<String, String> headers,
                          Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(Method.GET, url, clazz, headers, listener, errorListener);
//        setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
