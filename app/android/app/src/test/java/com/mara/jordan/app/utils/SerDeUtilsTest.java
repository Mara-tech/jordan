package com.mara.jordan.app.utils;

import com.google.gson.JsonSyntaxException;
import com.mara.jordan.app.db.JordanServer;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Locale;

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
                .build();
        String json = SerDeUtils.serialize(server);
        assertNotNull(json);
        // @Expose fields
        assert json.contains("\"name\"");
        assert json.contains("\"url\"");
        assert json.contains("\"login\"");
    }

    @Test
    public void serialize_carriesNoPassword() {
        JordanServer server = JordanServer.builder()
                .name("prod")
                .url("https://example.com/jordan/admin")
                .login("admin")
                .build();
        String json = SerDeUtils.serialize(server);
        // the entity holds no secret at all : passwords live in JordanSecretStore, so no export
        // — this one or any other format — can carry one
        assertFalse(json.contains("password"));
    }

    @Test
    public void deserialize_validJson_returnsPopulatedObject() {
        String json = "{\"name\":\"prod\",\"url\":\"https://example.com\",\"login\":\"admin\"}";
        JordanServer server = SerDeUtils.deserialize(json, JordanServer.class);
        assertNotNull(server);
        assertEquals("prod", server.getName());
        assertEquals("https://example.com", server.getUrl());
        assertEquals("admin", server.getLogin());
    }

    @Test
    public void deserialize_legacyExportWithPassword_dropsIt() {
        // exports produced before the secret moved out of the database still carry the field
        String json = "{\"name\":\"prod\",\"url\":\"https://example.com\",\"login\":\"admin\",\"password\":\"secret\"}";
        JordanServer server = SerDeUtils.deserialize(json, JordanServer.class);
        assertEquals("admin", server.getLogin());
        assertEquals("prod", server.getName());
        // there is no field to read the password into : it is ignored, not imported
    }

    @Test
    public void entity_holdsNoSecretField() {
        // the guarantee the export relies on, checked on the class rather than on one format :
        // a CSV or text export added later cannot leak what the entity does not hold
        for (Field field : JordanServer.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertFalse(field.getName(), name.contains("password"));
            assertFalse(field.getName(), name.contains("secret"));
            assertFalse(field.getName(), name.contains("token"));
        }
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
