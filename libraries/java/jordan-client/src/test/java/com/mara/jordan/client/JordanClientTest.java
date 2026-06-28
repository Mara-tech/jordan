package com.mara.jordan.client;

import com.mara.jordan.core.JordanConstants;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JordanClientTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private String baseUrl() {
        return server.url("/jordan/").toString();
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    public void testRegister() throws IOException, InterruptedException {
        enqueueRegister(42, "tok123");
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test-client")) {
            assertEquals(42L, instance.getTaskId());
            assertEquals("test-client", instance.getName());
        }

        RecordedRequest req = server.takeRequest();
        assertEquals("/jordan/client/register", req.getPath());
        assertEquals("POST", req.getMethod());
        assertTrue(req.getBody().readUtf8().contains("test-client"));
    }

    @Test
    public void testRegisterWithActionsAndPassword() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueUnregister();

        List<Map<String, Object>> actions = ActionBuilder
                .withAction("restart")
                .withParameter("delay", JordanConstants.PARAMETER_TYPE_INT)
                .build();

        try (JordanInstance instance = Jordan.register(baseUrl(), "my-script", actions, "s3cr3t")) {
            assertEquals(1L, instance.getTaskId());
        }

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("my-script"));
        assertTrue(body.contains("s3cr3t"));
        assertTrue(body.contains("restart"));
    }

    @Test(expected = IOException.class)
    public void testRegisterThrowsOnFailure() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));
        Jordan.register(baseUrl(), "test");
    }

    @Test
    public void testRegisterSendsAuthorizationOnClose() throws IOException, InterruptedException {
        enqueueRegister(5, "mytoken");
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            // close() calls unregister
        }

        server.takeRequest(); // register (no auth header expected)
        RecordedRequest unregReq = server.takeRequest();
        assertEquals("Bearer mytoken", unregReq.getHeader("Authorization"));
    }

    // -------------------------------------------------------------------------
    // sendStatus / sendProgress / sendSuccessStatus / sendFailureStatus
    // -------------------------------------------------------------------------

    @Test
    public void testSendStatus() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(99);
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            String statusId = instance.sendStatus("processing");
            assertEquals("99", statusId);
        }

        server.takeRequest(); // register
        RecordedRequest req = server.takeRequest();
        assertEquals("/jordan/client/1/status", req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("processing"));
        assertTrue(body.contains("general"));
    }

    @Test
    public void testSendProgress() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(10);
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            instance.sendProgress("50%");
        }

        server.takeRequest();
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("progress"));
        assertTrue(body.contains("50%"));
    }

    @Test
    public void testSendSuccessStatus() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(11);
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            instance.sendSuccessStatus("done");
        }

        server.takeRequest();
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("success"));
    }

    @Test
    public void testSendFailureStatus() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(12);
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            instance.sendFailureStatus("exploded");
        }

        server.takeRequest();
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("failure"));
        assertTrue(body.contains("exploded"));
    }

    @Test
    public void testStatusPayloadContainsTimestamp() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(1);
        enqueueUnregister();

        long before = System.currentTimeMillis() / 1000L;
        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            instance.sendStatus("ping");
        }
        long after = System.currentTimeMillis() / 1000L;

        server.takeRequest();
        String body = server.takeRequest().getBody().readUtf8();
        // timestamp field must be present and reasonable
        assertTrue(body.contains("timestamp"));
        // crude check: value must appear somewhere in the body
        boolean foundTimestamp = false;
        for (long ts = before; ts <= after; ts++) {
            if (body.contains(String.valueOf(ts))) {
                foundTimestamp = true;
                break;
            }
        }
        assertTrue("timestamp should be present in payload", foundTimestamp);
    }

    // -------------------------------------------------------------------------
    // readMessage
    // -------------------------------------------------------------------------

    @Test
    public void testReadMessage() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"messageId\":10,\"action\":{\"actionName\":\"doWork\",\"placeholders\":{\"file\":\"report.csv\"}}}")
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(202)); // received()
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            JordanMessage msg = instance.readMessage();
            assertNotNull(msg);
            assertEquals(10L, msg.getMessageId());
            assertEquals("doWork", msg.getActionName());
            assertEquals("report.csv", msg.getPlaceholder("file"));
        }

        server.takeRequest(); // register
        RecordedRequest getReq = server.takeRequest();
        assertEquals("/jordan/client/1/message", getReq.getPath());
        assertEquals("GET", getReq.getMethod());

        // readMessage() must immediately call received()
        RecordedRequest receivedReq = server.takeRequest();
        assertEquals("/jordan/client/1/10/CLIENT_RECEIVED", receivedReq.getPath());
        assertEquals("PUT", receivedReq.getMethod());
    }

    @Test
    public void testReadMessageReturnsNullWhenNoneAvailable() throws IOException {
        enqueueRegister(1, "tok");
        server.enqueue(new MockResponse().setResponseCode(204));
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            assertNull(instance.readMessage());
        }
    }

    // -------------------------------------------------------------------------
    // complete / unregister
    // -------------------------------------------------------------------------

    @Test
    public void testComplete() throws IOException, InterruptedException {
        enqueueRegister(3, "tok");
        server.enqueue(new MockResponse().setResponseCode(202));
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            assertTrue(instance.complete());
        }

        server.takeRequest();
        RecordedRequest req = server.takeRequest();
        assertEquals("/jordan/client/3/COMPLETE", req.getPath());
        assertEquals("PUT", req.getMethod());
    }

    @Test
    public void testUnregister() throws IOException, InterruptedException {
        enqueueRegister(7, "tok");
        enqueueUnregister();

        JordanInstance instance = Jordan.register(baseUrl(), "test");
        assertTrue(instance.unregister());

        server.takeRequest();
        RecordedRequest req = server.takeRequest();
        assertEquals("/jordan/client/7/unregister", req.getPath());
        assertEquals("POST", req.getMethod());
    }

    // -------------------------------------------------------------------------
    // fatal
    // -------------------------------------------------------------------------

    @Test
    public void testFatalSendsFailureAndErrorAndUnregisters() throws IOException, InterruptedException {
        enqueueRegister(2, "tok");
        enqueueStatus(1);                                           // sendFailureStatus
        server.enqueue(new MockResponse().setResponseCode(202));   // updateTask ERROR
        enqueueUnregister();

        JordanInstance instance = Jordan.register(baseUrl(), "test");
        instance.fatal(new RuntimeException("boom"));

        server.takeRequest(); // register
        String failureBody = server.takeRequest().getBody().readUtf8();
        assertTrue(failureBody.contains("failure"));
        assertTrue(failureBody.contains("boom"));

        RecordedRequest errorReq = server.takeRequest();
        assertEquals("/jordan/client/2/ERROR", errorReq.getPath());

        RecordedRequest unregReq = server.takeRequest();
        assertEquals("/jordan/client/2/unregister", unregReq.getPath());
    }

    // -------------------------------------------------------------------------
    // createTask / JordanTaskInstance
    // -------------------------------------------------------------------------

    @Test
    public void testCreateTask() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"taskId\": 55}")
                .addHeader("Content-Type", "application/json"));
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "parent")) {
            JordanTaskInstance task = instance.createTask("sub-task");
            assertNotNull(task);
            assertEquals(55L, task.getTaskId());
            assertEquals("sub-task", task.getName());
        }

        server.takeRequest();
        RecordedRequest taskReq = server.takeRequest();
        assertEquals("/jordan/client/1/task", taskReq.getPath());
        assertEquals("POST", taskReq.getMethod());
        assertTrue(taskReq.getBody().readUtf8().contains("sub-task"));
    }

    @Test
    public void testTaskInstanceFatalDoesNotUnregister() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"taskId\": 20}")
                .addHeader("Content-Type", "application/json"));
        enqueueStatus(5);                                          // sendFailureStatus on sub-task
        server.enqueue(new MockResponse().setResponseCode(202));  // updateTask ERROR on sub-task
        enqueueUnregister();                                       // close() on parent only

        try (JordanInstance instance = Jordan.register(baseUrl(), "parent")) {
            JordanTaskInstance task = instance.createTask("sub");
            task.fatal(new RuntimeException("subtask exploded"));
        }

        // Exactly 5 requests: register, createTask, failureStatus, ERROR, unregister(parent)
        assertEquals(5, server.getRequestCount());
        server.takeRequest(); // register
        server.takeRequest(); // createTask
        server.takeRequest(); // sendFailureStatus (sub-task id=20)
        RecordedRequest errorReq = server.takeRequest();
        assertEquals("/jordan/client/20/ERROR", errorReq.getPath());
        RecordedRequest unregReq = server.takeRequest();
        assertEquals("/jordan/client/1/unregister", unregReq.getPath()); // parent, not sub-task
    }

    @Test
    public void testTaskInstanceCloseIsNoOp() throws IOException {
        enqueueRegister(1, "tok");
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"taskId\": 9}")
                .addHeader("Content-Type", "application/json"));
        enqueueUnregister(); // parent only

        try (JordanInstance instance = Jordan.register(baseUrl(), "parent")) {
            try (JordanTaskInstance task = instance.createTask("sub")) {
                // sub-task close() is a no-op
            }
        }

        assertEquals(3, server.getRequestCount()); // register + createTask + unregister(parent)
    }

    // -------------------------------------------------------------------------
    // ActionBuilder
    // -------------------------------------------------------------------------

    @Test
    public void testActionBuilderMultipleActions() {
        List<Map<String, Object>> actions = ActionBuilder
                .withAction("doSomething")
                .withParameter("filename", JordanConstants.PARAMETER_TYPE_STRING)
                .withParameter("count", JordanConstants.PARAMETER_TYPE_INT, 1)
                .addAction("cancel")
                .build();

        assertEquals(2, actions.size());
        assertEquals("doSomething", actions.get(0).get("actionName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) actions.get(0).get("parameters");
        assertEquals(2, params.size());
        assertEquals("filename", params.get(0).get("name"));
        assertEquals("string", params.get(0).get("type"));
        assertNull(params.get(0).get("defaultValue"));
        assertEquals(1, params.get(1).get("defaultValue"));

        assertEquals("cancel", actions.get(1).get("actionName"));
        assertNull("action without params should have no parameters key", actions.get(1).get("parameters"));
    }

    @Test
    public void testActionBuilderNoParameters() {
        List<Map<String, Object>> actions = ActionBuilder.withAction("ping").build();
        assertEquals(1, actions.size());
        assertEquals("ping", actions.get(0).get("actionName"));
        assertNull(actions.get(0).get("parameters"));
    }

    @Test
    public void testActionBuilderFloatParameter() {
        List<Map<String, Object>> actions = ActionBuilder
                .withAction("scale")
                .withParameter("factor", JordanConstants.PARAMETER_TYPE_FLOAT, 1.5)
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) actions.get(0).get("parameters");
        assertEquals("float", params.get(0).get("type"));
        assertEquals(1.5, params.get(0).get("defaultValue"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testActionBuilderRejectsInvalidType() {
        ActionBuilder.withAction("test").withParameter("p", "boolean");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void enqueueRegister(long taskId, String authToken) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(String.format("{\"taskId\":%d,\"authToken\":\"%s\"}", taskId, authToken))
                .addHeader("Content-Type", "application/json"));
    }

    private void enqueueStatus(long statusId) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(String.format("{\"statusId\":%d}", statusId))
                .addHeader("Content-Type", "application/json"));
    }

    private void enqueueUnregister() {
        server.enqueue(new MockResponse().setResponseCode(200));
    }
}
