package com.mara.jordan.client;

import com.google.gson.Gson;
import com.mara.jordan.core.JordanConstants;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    public void testRegisterSendsNoAuthorizationWithoutRegistrationKey() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            // registration is open by default
        }

        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void testRegisterSendsRegistrationKeyAsBearerToken() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueUnregister();

        List<Map<String, Object>> noActions = Collections.emptyList();
        try (JordanInstance instance = Jordan.register(baseUrl(), "test", noActions, null, "reg-key-789")) {
            // key required by a server that closed registration
        }

        RecordedRequest req = server.takeRequest();
        assertEquals("Bearer reg-key-789", req.getHeader("Authorization"));
        assertFalse("the key must not reach the payload", req.getBody().readUtf8().contains("reg-key-789"));
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
        assertTrue(body.contains("\"type\":\"progress\""));
        // a JSON integer: the server moves the task's progress on nothing else
        assertTrue(body.contains("\"status\":50,"));
    }

    private String sentProgressJson(ProgressSender sender) throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(10);
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            sender.send(instance);
        }

        server.takeRequest();
        return server.takeRequest().getBody().readUtf8();
    }

    private interface ProgressSender {
        void send(JordanInstance instance) throws IOException;
    }

    @Test
    public void testSendProgressSendsATruncatedInteger() throws IOException, InterruptedException {
        assertTrue(sentProgressJson(i -> i.sendProgress(42.9)).contains("\"status\":42,"));
    }

    @Test
    public void testSendProgressReadsATextWithAPercentSign() throws IOException, InterruptedException {
        assertTrue(sentProgressJson(i -> i.sendProgress(" 42.5 % ")).contains("\"status\":42,"));
    }

    @Test
    public void testSendStatusConvertsAProgressToo() throws IOException, InterruptedException {
        assertTrue(sentProgressJson(i -> i.sendStatus("75", JordanConstants.STATUS_TYPE_PROGRESS)).contains("\"status\":75,"));
    }

    @Test
    public void testOtherStatusTypesAreSentAsGiven() throws IOException, InterruptedException {
        assertTrue(sentProgressJson(i -> i.sendStatus("75%")).contains("\"status\":\"75%\""));
    }

    @Test
    public void testSendProgressRefusesWhatIsNotAPercentage() throws IOException {
        enqueueRegister(1, "tok");
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            for (String text : new String[]{"50% done", "", "half", "-1", "100.5", "NaN", null}) {
                try {
                    instance.sendProgress(text);
                    fail("accepted " + text);
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage().contains("from 0 to 100"));
                }
            }
            for (double value : new double[]{-0.1, 100.01, Double.NaN, Double.POSITIVE_INFINITY}) {
                try {
                    instance.sendProgress(value);
                    fail("accepted " + value);
                } catch (IllegalArgumentException expected) {
                    // refused before any request
                }
            }
        }
        assertEquals(2, server.getRequestCount()); // register and unregister only
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

    // -------------------------------------------------------------------------
    // sendMetric
    // -------------------------------------------------------------------------

    private Map sentMetric(Double step) throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueStatus(13);
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            String statusId = step == null
                    ? instance.sendMetric("throughput", 120)
                    : instance.sendMetric("held-out loss", 0.6648, step);
            assertEquals("13", statusId);
        }

        server.takeRequest(); // register
        RecordedRequest req = server.takeRequest();
        assertEquals("/jordan/client/1/status", req.getPath());
        return new com.google.gson.Gson().fromJson(req.getBody().readUtf8(), Map.class);
    }

    @Test
    public void testSendMetricCarriesNameValueAndStep() throws IOException, InterruptedException {
        Map body = sentMetric(3.0);
        assertEquals(JordanConstants.STATUS_TYPE_METRIC, body.get("type"));
        Map metric = (Map) body.get("metric");
        assertEquals("held-out loss", metric.get("name"));
        assertEquals(0.6648, (Double) metric.get("value"), 0);
        assertEquals(3.0, (Double) metric.get("step"), 0);
        assertNotNull(body.get("timestamp"));
    }

    @Test
    public void testSendMetricReadsAsALogLine() throws IOException, InterruptedException {
        assertEquals("held-out loss = 0.6648 (step 3)", sentMetric(3.0).get("status"));
    }

    @Test
    public void testSendMetricWithoutStep() throws IOException, InterruptedException {
        Map body = sentMetric(null);
        assertFalse(((Map) body.get("metric")).containsKey("step"));
        assertEquals("throughput = 120", body.get("status"));
    }

    @Test
    public void testSendMetricSkipsWhatNoCurveCanHold() throws IOException, InterruptedException {
        enqueueRegister(1, "tok");
        enqueueUnregister();

        try (JordanInstance instance = Jordan.register(baseUrl(), "test")) {
            assertNull(instance.sendMetric("loss", Double.NaN));
            assertNull(instance.sendMetric("loss", Double.POSITIVE_INFINITY, 3.0));
            assertNull(instance.sendMetric("loss", 0.5, Double.NaN));
        }

        server.takeRequest(); // register
        assertEquals("/jordan/client/1/unregister", server.takeRequest().getPath());
    }

    @Test(expected = IOException.class)
    public void testSendMetricThrowsWhenRefused() throws IOException {
        enqueueRegister(1, "tok");
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"message\": \"metric 'name' must be a non-empty string\"}"));

        JordanInstance instance = Jordan.register(baseUrl(), "test");
        instance.sendMetric("", 0.5);
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
            assertTrue(msg.isReceiptConfirmed());
            assertNull(msg.getReceiptError());
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

    // The read takes the message off the queue, so an acknowledgement of receipt that fails must not lose it (JRD-27)

    @Test
    public void testReadMessageReturnsTheMessageWhenTheReceiptTimesOut() throws IOException {
        enqueueMessage(10);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); // received()

        JordanMessage msg = instanceWithReadTimeout().readMessage();

        assertNotNull(msg);
        assertEquals("doWork", msg.getActionName());
        assertFalse(msg.isReceiptConfirmed());
        assertTrue(msg.getReceiptError() instanceof SocketTimeoutException);
    }

    @Test
    public void testReadMessageReturnsTheMessageWhenTheReceiptIsRefused() throws IOException {
        enqueueMessage(10);
        server.enqueue(new MockResponse().setResponseCode(500)); // received()

        JordanMessage msg = instanceWithReadTimeout().readMessage();

        assertNotNull(msg);
        assertFalse(msg.isReceiptConfirmed());
        assertNull("an answer, even a refusal, is not an error", msg.getReceiptError());
    }

    @Test
    public void testReceivedRetriesAnUnconfirmedReceipt() throws IOException, InterruptedException {
        enqueueMessage(10);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); // received(), from readMessage()
        server.enqueue(new MockResponse().setResponseCode(202)); // received(), retried

        JordanMessage msg = instanceWithReadTimeout().readMessage();
        assertTrue(msg.received());

        assertTrue(msg.isReceiptConfirmed());
        assertNull("the error of an earlier attempt does not outlive a successful one", msg.getReceiptError());
        server.takeRequest(); // read
        assertEquals("/jordan/client/1/10/CLIENT_RECEIVED", server.takeRequest().getPath());
        assertEquals("/jordan/client/1/10/CLIENT_RECEIVED", server.takeRequest().getPath());
    }

    @Test
    public void testReadMessageThrowsWhenTheReadItselfFails() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); // the read

        JordanInstance instance = instanceWithReadTimeout();

        assertThrows(SocketTimeoutException.class, instance::readMessage);
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

    private void enqueueMessage(long messageId) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(String.format("{\"messageId\":%d,\"action\":{\"actionName\":\"doWork\",\"placeholders\":{}}}", messageId))
                .addHeader("Content-Type", "application/json"));
    }

    /** An instance of task 1 that gives up on an unanswered request after a fraction of a second, without registering. */
    private JordanInstance instanceWithReadTimeout() {
        OkHttpClient httpClient = new OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build();
        return new JordanInstance(baseUrl(), 1, "tok", "test", httpClient, new Gson());
    }

    private void enqueueUnregister() {
        server.enqueue(new MockResponse().setResponseCode(200));
    }
}
