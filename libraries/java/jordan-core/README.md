# jordan-core

Shared Java library for the Jordan ecosystem — DTOs, constants, and utilities used by both `jordan-client` and the Android app.

## Contents

| Package | Description |
|---|---|
| `com.mara.jordan.core.dto` | 13 Lombok-annotated DTOs mirroring the REST API payloads |
| `com.mara.jordan.core.JordanConstants` | All protocol string constants (states, status types, parameter types) |
| `com.mara.jordan.core.DateUtils` | Timestamp formatting (seconds → locale-aware string) |
| `com.mara.jordan.core.SerDeUtils` | Gson-based JSON serialization/deserialization |
| `com.mara.jordan.core.JordanHelper` | Active-client utilities (task counts, progress estimation, message state) |

## Requirements

- Java 8+
- Gradle 7+ (for building from source)

## Build

```bash
# from libraries/java/
./gradlew :jordan-core:build
```

## DTOs

All DTOs use Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`. They are plain Java objects with no Android or framework dependency.

| Class | Description |
|---|---|
| `JordanClientDTO` | A registered passive client with its tasks |
| `JordanTaskDTO` | A task (root or sub-task) with state, progress, and action definitions |
| `JordanStatusDTO` | A status update sent by a passive client |
| `JordanMessageStateDTO` | A message with its full state audit trail |
| `JordanMessageStateAuditDTO` | One entry in a message's audit trail (timestamp + state) |
| `JordanExecutedActionDTO` | An action that was triggered, with its placeholder values |
| `JordanActionDefinitionDTO` | Definition of an action (name + parameter list) |
| `JordanActionDefinitionWithTaskDTO` | Action definition enriched with its parent task |
| `JordanActionParameterDTO` | One parameter of an action definition |
| `JordanParentTaskDTO` | Lightweight task reference (id, name, state, progress) |
| `JordanSendMessageDTO` | Payload for sending a message to a passive client |
| `JordanSendMessageActionDTO` | Action part of a `JordanSendMessageDTO` |
| `JordanTestDTO` | Server connectivity check response |

## Constants (`JordanConstants`)

```java
// Task states
JordanConstants.TASK_STARTED_STATE    // "STARTED"
JordanConstants.TASK_RUNNING_STATE    // "RUNNING"
JordanConstants.TASK_COMPLETE_STATE   // "COMPLETE"
JordanConstants.TASK_ERROR_STATE      // "ERROR"
JordanConstants.TASK_TIME_OUT_STATE   // "TIME_OUT"
JordanConstants.TASK_PAUSED_STATE     // "PAUSED"

// Client states
JordanConstants.CLIENT_REGISTERED_STATE     // "REGISTERED"
JordanConstants.CLIENT_UNREGISTERED_STATE   // "UNREGISTERED"

// Status types
JordanConstants.STATUS_TYPE_GENERAL    // "general"
JordanConstants.STATUS_TYPE_PROGRESS   // "progress"
JordanConstants.STATUS_TYPE_SUCCESS    // "success"
JordanConstants.STATUS_TYPE_FAILURE    // "failure"

// Message state machine
JordanConstants.MESSAGE_STATE_SERVER_RECEIVED        // "SERVER_RECEIVED"
JordanConstants.MESSAGE_STATE_DELIVERED              // "MESSAGE_DELIVERED"
JordanConstants.MESSAGE_STATE_CLIENT_RECEIVED        // "CLIENT_RECEIVED"
JordanConstants.MESSAGE_STATE_ACKNOWLEDGED           // "MESSAGE_ACKNOWLEDGED"
JordanConstants.MESSAGE_STATE_PROCESSED              // "MESSAGE_PROCESSED"
JordanConstants.MESSAGE_STATE_ERROR_CANNOT_PROCESS   // "ERROR_CANNOT_PROCESS_MESSAGE"
JordanConstants.MESSAGE_STATE_OVERRIDDEN             // "MESSAGE_OVERRIDDEN"

// Action parameter types
JordanConstants.PARAMETER_TYPE_STRING   // "string"
JordanConstants.PARAMETER_TYPE_INT      // "int"
JordanConstants.PARAMETER_TYPE_FLOAT    // "float"
```

## Running the tests

```bash
# from libraries/java/
./gradlew :jordan-core:test
```
