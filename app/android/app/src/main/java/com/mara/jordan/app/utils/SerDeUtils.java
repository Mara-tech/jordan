package com.mara.jordan.app.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class SerDeUtils {
    private static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    public static <T> String serialize(T item){
        return gson.toJson(item);
    }

    public static <T> T deserialize(String data, Class<T> clazz) throws JsonSyntaxException {
        return gson.fromJson(data, clazz);
    }
}
