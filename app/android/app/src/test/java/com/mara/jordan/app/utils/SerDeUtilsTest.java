package com.mara.jordan.app.utils;

import com.google.gson.JsonSyntaxException;
import com.mara.jordan.app.db.JordanServer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SerDeUtilsTest {

    @Test
    public void serialize_exposedFields_areIncluded() {
        JordanServer server = JordanServer.builder()
                .name("prod")
                .url("https://example.com/jordan/admin")
                .login("admin")
                .password("secret")
                .build();
        String json = SerDeUtils.serialize(server);
        assertNotNull(json);
        // @Expose fields
        assert json.contains("\"name\"");
        assert json.contains("\"url\"");
        assert json.contains("\"login\"");
    }

    @Test
    public void serialize_password_isExcluded() {
        JordanServer server = JordanServer.builder()
                .name("prod")
                .url("https://example.com/jordan/admin")
                .login("admin")
                .password("secret")
                .build();
        String json = SerDeUtils.serialize(server);
        // password has @Expose(serialize=false) — must not appear in JSON
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("password"));
    }

    @Test
    public void deserialize_validJson_returnsPopulatedObject() {
        String json = "{\"name\":\"prod\",\"url\":\"https://example.com\",\"login\":\"admin\",\"password\":\"secret\"}";
        JordanServer server = SerDeUtils.deserialize(json, JordanServer.class);
        assertNotNull(server);
        assertEquals("prod", server.getName());
        assertEquals("https://example.com", server.getUrl());
        assertEquals("admin", server.getLogin());
        // password has @Expose(serialize=false) but deserialize=true (default), so it IS read on deserialize
        assertEquals("secret", server.getPassword());
    }

    @Test
    public void roundTrip_nameUrlLogin_preserved() {
        JordanServer original = JordanServer.builder()
                .name("my-server")
                .url("http://192.168.1.1:5000/jordan/admin")
                .login("operator")
                .build();
        String json = SerDeUtils.serialize(original);
        JordanServer deserialized = SerDeUtils.deserialize(json, JordanServer.class);
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getUrl(), deserialized.getUrl());
        assertEquals(original.getLogin(), deserialized.getLogin());
    }

    @Test(expected = JsonSyntaxException.class)
    public void deserialize_malformedJson_throwsException() {
        SerDeUtils.deserialize("{not valid json", JordanServer.class);
    }

    @Test
    public void deserialize_missingFields_setsNulls() {
        String json = "{\"name\":\"only-name\"}";
        JordanServer server = SerDeUtils.deserialize(json, JordanServer.class);
        assertEquals("only-name", server.getName());
        assertNull(server.getUrl());
        assertNull(server.getLogin());
    }
}
