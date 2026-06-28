package com.mara.jordan.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class SerDeUtils {

    private static final Gson GSON = new Gson();

    public static <T> String serialize(T item) {
        return GSON.toJson(item);
    }

    public static <T> T deserialize(String data, Class<T> clazz) throws JsonSyntaxException {
        return GSON.fromJson(data, clazz);
    }

    private SerDeUtils() {}
}
