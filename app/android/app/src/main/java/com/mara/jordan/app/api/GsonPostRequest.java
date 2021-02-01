package com.mara.jordan.app.api;

import androidx.annotation.Nullable;

import com.android.volley.Response;

import java.util.Map;

public class GsonPostRequest<T> extends AGsonRequestWithBody<T> {
    private static final String TAG = "GsonPostRequest";

    /**
     * Make a POST request and return a parsed object from JSON.
     *
     * @param url URL of the request to make
     * @param requestBody a pojo to pass in the request body.
     * @param clazz Relevant class object, for Gson's reflection for response
     * @param headers Map of request headers
     */
    public GsonPostRequest(String url, @Nullable Object requestBody, Class<T> clazz, Map<String, String> headers,
                           Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(Method.POST, url, requestBody, clazz, headers, listener, errorListener);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
