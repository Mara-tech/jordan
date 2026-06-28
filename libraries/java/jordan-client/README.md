# jordan-client

Java passive-client library for [Jordan](../../README.md) — the Java counterpart of `jordan_py`.

Lets a Java program register with the Jordan server, send status updates, and read messages sent by an operator.

## Requirements

- Java 11+
- Gradle 7+ (for building from source)

## Build

```bash
# from libraries/java/
./gradlew :jordan-client:build
```

## Quick start

```java
import com.mara.jordan.client.Jordan;
import com.mara.jordan.client.JordanInstance;
import com.mara.jordan.client.JordanMessage;

// Register with the server (creates a root task)
try (JordanInstance j = Jordan.register("http://localhost:5000/jordan/", "my-java-job")) {

    j.sendStatus("Starting…");

    // Read an incoming message (returns null if none)
    JordanMessage msg = j.readMessage();
    if (msg != null) {
        System.out.println("Action: " + msg.getActionName());
        System.out.println("File: " + msg.getPlaceholder("filename"));
        msg.acknowledgeAndProcessed();
    }

    j.sendProgress("75%");
    j.complete();

} // close() calls unregister() automatically
```

## Register with actions

```java
import com.mara.jordan.client.ActionBuilder;
import com.mara.jordan.core.JordanConstants;
import java.util.List;
import java.util.Map;

List<Map<String, Object>> actions = ActionBuilder
    .withAction("processFile")
    .withParameter("filename", JordanConstants.PARAMETER_TYPE_STRING)
    .withParameter("priority", JordanConstants.PARAMETER_TYPE_INT, 1)
    .addAction("cancel")
    .build();

try (JordanInstance j = Jordan.register("http://localhost:5000/jordan/", "my-job", actions)) {
    // …
}
```

## Sub-tasks

```java
try (JordanInstance j = Jordan.register("http://localhost:5000/jordan/", "pipeline")) {

    JordanTaskInstance step1 = j.createTask("extract");
    step1.sendStatus("Extracting…");
    step1.complete();

    JordanTaskInstance step2 = j.createTask("transform");
    step2.sendProgress("50%");
    step2.complete();

    j.complete();
}
// close() unregisters the root task only; sub-tasks are not unregistered
```

## Error handling

```java
try (JordanInstance j = Jordan.register("http://localhost:5000/jordan/", "job")) {
    try {
        riskyOperation();
        j.complete();
    } catch (Exception e) {
        j.fatal(e); // sends failure status + marks ERROR + unregisters
        // do NOT call close() again — fatal() already unregistered
    }
}
```

## API reference

### `Jordan` (factory)

| Method | Description |
|---|---|
| `register(url, name)` | Register with no actions |
| `register(url, name, actions)` | Register with action definitions |
| `register(url, name, actions, password)` | Register with password |

### `JordanInstance` / `JordanTaskInstance`

| Method | Description |
|---|---|
| `sendStatus(msg)` | Send a general status |
| `sendStatus(msg, type)` | Send a typed status |
| `sendProgress(msg)` | Send a progress status |
| `sendSuccessStatus(msg)` | Send a success status |
| `sendFailureStatus(msg)` | Send a failure status |
| `readMessage()` | Read next message (null if none); auto-calls `received()` |
| `createTask(name)` | Create a sub-task |
| `complete()` | Mark task as COMPLETE |
| `updateTask(state)` | Transition task to arbitrary state |
| `unregister()` | Unregister the root client (`JordanInstance` only) |
| `fatal(e)` | Send failure, mark ERROR, unregister |
| `close()` | Calls `unregister()` (use with try-with-resources) |

> **Note:** `JordanTaskInstance.fatal()` marks the sub-task as ERROR but does **not** unregister the parent client. `JordanTaskInstance.close()` is a no-op.

### `JordanMessage`

| Method | Description |
|---|---|
| `getActionName()` | Name of the triggered action |
| `getPlaceholder(key)` | Value of a specific placeholder |
| `getPlaceholders()` | All placeholders as an unmodifiable map |
| `received()` | Mark as CLIENT_RECEIVED (called automatically by `readMessage()`) |
| `acknowledge()` | Mark as MESSAGE_ACKNOWLEDGED |
| `processed()` | Mark as MESSAGE_PROCESSED |
| `acknowledgeAndProcessed()` | Shortcut for acknowledge then processed |
| `cannotProcess()` | Mark as ERROR_CANNOT_PROCESS_MESSAGE |
| `overridden()` | Mark as MESSAGE_OVERRIDDEN |

### `ActionBuilder`

```java
ActionBuilder
    .withAction("actionName")          // start a new action (static factory)
    .withParameter("name", type)       // add a required parameter
    .withParameter("name", type, def)  // add a parameter with a default value
    .addAction("anotherAction")        // chain a second action
    .build();                          // returns List<Map<String, Object>>
```

Valid parameter types: `JordanConstants.PARAMETER_TYPE_STRING`, `PARAMETER_TYPE_INT`, `PARAMETER_TYPE_FLOAT`.

## Running the tests

```bash
# from libraries/java/
./gradlew :jordan-client:test
```
