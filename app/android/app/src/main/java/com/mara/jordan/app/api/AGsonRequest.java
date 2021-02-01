package com.mara.jordan.app.api;

import com.android.volley.Response;

import java.util.Map;

/**
 * https://developer.android.com/training/volley/request-custom#example:-gsonrequest
 * https://stackoverflow.com/questions/36432152/android-volley-gson-post, mixed with {@link com.android.volley.toolbox.JsonRequest}
 */
public abstract class AGsonRequest<T> extends AGsonRequestWithBody<T> {

    /**
     * Make a request and return a parsed object from JSON.
     *
     * @param method http method (from {@link Method}
     * @param url URL of the request to make
     * @param clazz Relevant class object, for Gson's reflection for response
     * @param headers Map of request headers
     */
    public AGsonRequest(int method, String url, Class<T> clazz, Map<String, String> headers,
                             Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(method, url, null, clazz, headers, listener, errorListener);
    }
}
