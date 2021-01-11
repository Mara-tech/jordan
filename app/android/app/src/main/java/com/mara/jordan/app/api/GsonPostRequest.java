package com.mara.jordan.app.api;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * https://developer.android.com/training/volley/request-custom#example:-gsonrequest
 * https://stackoverflow.com/questions/36432152/android-volley-gson-post, mixed with {@link com.android.volley.toolbox.JsonRequest}
 */
public class GsonPostRequest<T> extends Request<T> {
    private static final String TAG = "GsonPostRequest";
    private final Gson gson = new Gson();
    private final Class<T> clazz;
    private final Map<String, String> headers;
    private final Response.Listener<T> listener;
    private final Object mRequestBody;

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
        super(Method.POST, url, errorListener);
        this.mRequestBody = requestBody;
        this.clazz = clazz;
        this.headers = headers;
        this.listener = listener;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers != null ? headers : super.getHeaders();
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = new String(
                    response.data,
                    HttpHeaderParser.parseCharset(response.headers));
            return Response.success(
                    gson.fromJson(json, clazz),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (JsonSyntaxException e) {
            return Response.error(new ParseError(e));
        }
    }


    @Override
    public byte[] getBody() throws AuthFailureError {
        Log.i(TAG, "Request body : " + gson.toJson(mRequestBody));
        return gson.toJson(mRequestBody).getBytes();
    }
}
