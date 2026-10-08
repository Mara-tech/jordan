package com.mara.jordan.client;

import java.io.IOException;

/**
 * The server answered a read with a message the library could not decode — a body cut short, not JSON, or missing
 * a field. The server took that message off the queue when it answered, so the raw {@link #getBody() body} is the
 * only copy left: it is kept here, and logged before this is thrown (JRD-33, the contract JRD-29 set for jordan_py).
 * An {@link IOException}, the one {@link JordanInstance#readMessage()} already declares; the decoding error is the
 * {@link #getCause() cause}.
 */
public class UndecodableMessageException extends IOException {

    private final long taskId;
    private final int statusCode;
    private final String body;

    UndecodableMessageException(long taskId, int statusCode, String body, RuntimeException cause) {
        super(String.format("Could not decode the message read for task %d (HTTP %d, %s: %s); raw body: '%s'",
                taskId, statusCode, cause.getClass().getSimpleName(), cause.getMessage(), body), cause);
        this.taskId = taskId;
        this.statusCode = statusCode;
        this.body = body;
    }

    /** The task the message was read for. */
    public long getTaskId() { return taskId; }
    /** The HTTP status of the read — a 200: the server handed the message out. */
    public int getStatusCode() { return statusCode; }
    /** The raw body of the answer, as received. */
    public String getBody() { return body; }
}
