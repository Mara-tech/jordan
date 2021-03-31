package com.mara.jordan.app.api;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;

import java.util.Map;

public class StringRequest extends com.android.volley.toolbox.StringRequest {


    private final Map<String, String> headers;

    /**
     * Same as {@link com.android.volley.toolbox.StringRequest#StringRequest(int, java.lang.String, com.android.volley.Response.Listener, com.android.volley.Response.ErrorListener)} with headers.
     * @param method http method (from {@link Method}
     * @param url URL of the request to make
     * @param headers Map of request headers
     */
    public StringRequest(int method, String url, Map<String, String> headers,
                         Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(method, url, listener, errorListener);
        this.headers = headers;
    }

    /**
     * Same as {@link com.android.volley.toolbox.StringRequest#StringRequest(java.lang.String, com.android.volley.Response.Listener, com.android.volley.Response.ErrorListener)} with headers.
     * @param url URL of the request to make
     * @param headers Map of request headers
     */
    public StringRequest(String url, Map<String, String> headers,
                         Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(url, listener, errorListener);
        this.headers = headers;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers != null ? headers : super.getHeaders();
    }
}
