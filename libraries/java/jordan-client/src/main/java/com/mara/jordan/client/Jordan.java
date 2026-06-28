package com.mara.jordan.client;

import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Factory for creating passive-client instances. Mirror of the Python {@code jordan_py.register()} function. */
public final class Jordan {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private Jordan() {}

    public static JordanInstance register(String serverBaseUrl, String clientName) throws IOException {
        return register(serverBaseUrl, clientName, Collections.<Map<String, Object>>emptyList(), null);
    }

    public static JordanInstance register(String serverBaseUrl, String clientName, List<Map<String, Object>> actions) throws IOException {
        return register(serverBaseUrl, clientName, actions, null);
    }

    public static JordanInstance register(String serverBaseUrl, String clientName, List<Map<String, Object>> actions, String password) throws IOException {
        String baseUrl = serverBaseUrl.endsWith("/") ? serverBaseUrl : serverBaseUrl + "/";
        OkHttpClient httpClient = new OkHttpClient();
        Gson gson = new Gson();

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", clientName);
        if (password != null) payload.put("password", password);
        if (!actions.isEmpty()) payload.put("actions", actions);

        Request request = new Request.Builder()
                .url(baseUrl + "client/register")
                .post(RequestBody.create(gson.toJson(payload), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200) {
                Map result = gson.fromJson(response.body().string(), Map.class);
                long taskId = ((Number) result.get("taskId")).longValue();
                String authToken = (String) result.get("authToken");
                return new JordanInstance(baseUrl, taskId, authToken, clientName, httpClient, gson);
            }
            throw new IOException("register failed: HTTP " + response.code() + " - " + response.body().string());
        }
    }
}
