package com.mara.jordan.core;

import com.google.gson.JsonSyntaxException;
import com.mara.jordan.core.dto.JordanParentTaskDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;
import org.junit.Test;

import static org.junit.Assert.*;

public class SerDeUtilsTest {

    @Test
    public void testSerializeProducesJson() {
        JordanStatusDTO dto = JordanStatusDTO.builder()
                .statusId(1L).type("general").status("running").timestamp(1000L).build();
        String json = SerDeUtils.serialize(dto);
        assertTrue(json.contains("running"));
        assertTrue(json.contains("general"));
        assertTrue(json.contains("1000"));
    }

    @Test
    public void testDeserializePopulatesFields() {
        String json = "{\"statusId\":42,\"type\":\"progress\",\"status\":\"50%\",\"timestamp\":999}";
        JordanStatusDTO dto = SerDeUtils.deserialize(json, JordanStatusDTO.class);
        assertEquals(42L, dto.getStatusId());
        assertEquals("progress", dto.getType());
        assertEquals("50%", dto.getStatus());
        assertEquals(999L, dto.getTimestamp());
    }

    @Test
    public void testRoundTrip() {
        JordanStatusDTO original = JordanStatusDTO.builder()
                .statusId(7L).type("success").status("done").timestamp(1234567890L).build();
        JordanStatusDTO restored = SerDeUtils.deserialize(SerDeUtils.serialize(original), JordanStatusDTO.class);
        assertEquals(original.getStatusId(), restored.getStatusId());
        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
    }

    @Test
    public void testRoundTripWithNestedObject() {
        JordanStatusDTO dto = JordanStatusDTO.builder()
                .statusId(3L).type("general").status("ok").timestamp(100L)
                .parentTask(JordanParentTaskDTO.builder()
                        .taskId(99L).name("parent").state("RUNNING").progress(50).build())
                .build();
        JordanStatusDTO restored = SerDeUtils.deserialize(SerDeUtils.serialize(dto), JordanStatusDTO.class);
        assertNotNull(restored.getParentTask());
        assertEquals(99L, restored.getParentTask().getTaskId());
        assertEquals("RUNNING", restored.getParentTask().getState());
    }

    @Test
    public void testDeserializeIgnoresUnknownFields() {
        String json = "{\"statusId\":1,\"type\":\"general\",\"status\":\"x\",\"timestamp\":0,\"unknownField\":\"value\"}";
        JordanStatusDTO dto = SerDeUtils.deserialize(json, JordanStatusDTO.class);
        assertEquals(1L, dto.getStatusId());
    }

    @Test
    public void testSerializeNullFieldsOmitted() {
        JordanStatusDTO dto = JordanStatusDTO.builder().statusId(1L).build();
        String json = SerDeUtils.serialize(dto);
        // Gson omits null fields by default
        assertFalse(json.contains("parentTask"));
    }

    @Test(expected = JsonSyntaxException.class)
    public void testDeserializeInvalidJsonThrows() {
        SerDeUtils.deserialize("not-json{{{", JordanStatusDTO.class);
    }
}
