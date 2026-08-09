# Passive Client (executing program)

## Register
#### HTTP API v1
POST /register
Success response : client_id, session_auth_token. Code : 200 OK
#### API function(s)
    static register(
        url : Uri/String,
        [clientName : String,]
        [actions : JordanActionsDefinition,]
        [password : String,]
        [registrationKey : String,]
    ) : JordanInstance
#### Authentication
Open by default — anyone reaching the server can register. The response body contains `authToken`, which must be supplied as `Authorization: Bearer <authToken>` on all subsequent calls.

A server exposed publicly can close registration by setting `JORDAN_REGISTRATION_KEY`. Callers then
send that key as `Authorization: Bearer <registrationKey>`; a missing or wrong key is answered with
`401`. The key travels in the header and never in the payload, which the server logs and stores as
the client record. Client libraries take it as an optional `registrationKey` argument and fall back
to the `JORDAN_REGISTRATION_KEY` environment variable.

The key is an admission ticket, not a session credential: it only opens registration. What a client
uses afterwards is the `authToken` it was issued, which is its own.

A server may accept **several** named keys at once, so one can be replaced without a flag day —
publish the new key beside the old, move the clients over, then drop the old one. A caller still
presents a single key and cannot tell how many the server accepts.

Registration attempts are rate-limited per caller address (`JORDAN_REGISTRATION_RATE_LIMIT` per
`JORDAN_REGISTRATION_RATE_WINDOW` seconds, 20 per minute by default) whether they succeed or not, so
key guessing is throttled too. Over the limit, the server answers `429` until the window closes.

## New Task
Client can optionally create several tasks. Default behaviour works with a single default task.
#### HTTP API v1
POST {taskId}/task (or {clientId}/task ? would we want nested task ?)
Success response : 201 CREATED
#### API function(s)
from JordanInstance or JordanTask 

    create_task(
        name : String,
        [actions : JordanActionsDefinition,]
    ) : JordanTask
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.

## Task Complete
Update Task workflow status to COMPLETE.
#### HTTP API v1
PUT {taskId}/COMPLETE
Success response : 202 Accepted
#### API function(s)
from JordanInstance or JordanTask 

    complete(
    ) : Void
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.


## Send Status
Passive Client sends status which might be considered as logs useful on Active Client side.
This can be performance, functional or whatever kind of information. It intends to be keys for decision-making. 
#### HTTP API v1
POST {taskId}/status
Success response : status_id. Code : 200 OK
#### API function(s)
from JordanInstance or JordanTask 

    send_status(
        status : String/JordanStatus,
    ) : JordanSentStatus
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.


## Read Message
Get action an Active Client commanded.
#### HTTP API v1
GET {taskId}/message
Success response : JordanMessage or <empty>. Code : 200 OK if any, 204 No Content if no message
#### API function(s)
from JordanInstance or JordanTask 

    read_message(
    ) : JordanMessage
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.
#### Workflow
When a message is received here, status on server-side becomes MESSAGE_DELIVERED.
Developers should use 'Acknowledge Message' and 'Processed Message' functions to update Message workflow.

## Acknowledge Message
Update Message workflow status to MESSAGE_ACKNOWLEDGED.
#### HTTP API v1
PUT {taskId}/{messageId}
Success response : 202 Accepted
#### API function(s)
from JordanMessage 

    acknowledge(
    ) : Void
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.

## Processed Message
Update Message workflow status to MESSAGE_PROCESSED.
#### HTTP API v1
PUT {taskId}/{messageId}
Success response : 202 Accepted
#### API function(s)
from JordanMessage 

    processed(
    ) : Void
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.


## Complete
Tell a task(s) is(are) complete, but keep registration valid, so can create other tasks.
#### HTTP API v1
POST {taskId}/complete
Success response : 200 OK
#### API function(s)
from JordanInstance or JordanTask 

    complete(
    ) : JordanSentComplete
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.


## Unregister
Ends registration, no action will be accepted by the server from this client.
The server considers registration with a limited Time-To-Live, 
but Unregister function should be used before the program ends.
#### HTTP API v1
POST {clientId}/unregister
Success response : 200 OK
#### API function(s)
from JordanInstance

    unregister(
    ) : JordanSentUnregister
#### Authentication
`Authorization: Bearer <authToken>` — token issued at registration. 401 if missing or invalid.


## DTOs
Library eases access to following DTOs
### JordanActionsDefinition
#### Content
List of actions, and their prototype

    {
      "actions": [
        "break_loop",
        "take_snapshot",
        "send_state_by_email": {
          "parameters": [
            {
              "name": "e-mail recipient",
              "type": "string"
            }
          ]
        }
      ]
    }

### JordanStatus
#### Builder
Status types : ["success", "failure", "progress", "general"]. Default type is "general".
"progress" status type expects a float number from 0.0 to 1.0.
#### Content

    {
      "type": "success",
      "status": "operation X succeeded."
    }

    {
      "type": "general",
      "status": "loop state 30/150, inner loop state 65/230."
    }

    {
      "type": "progress",
      "status": 0.65
    }

#### Nice to have
more types : start_time, eta, or custom types






# Active Client (admin GUI, Bot, ...)

## Roles and permissions
Every `/admin/*` call is authenticated by `Authorization: Bearer <adminToken>` and authorized
by the role carried by that token.

| Role | read | send | delete |
|---|---|---|---|
| `viewer` | ✔ | | |
| `operator` | ✔ | ✔ | |
| `admin` | ✔ | ✔ | ✔ |

- **read** — list clients, list actions, read statuses, read messages, generic query
- **send** — send a message (command) to a passive client
- **delete** — delete a task, a client, or the whole base

Two kinds of admin token are accepted:

- a **session token** issued by Login, tied to an operator account and expiring on its own;
- the **shared bootstrap token** (`JORDAN_ADMIN_TOKEN` on the server), which carries every
  permission under the conventional login `shared-admin`. It exists for machine-to-machine use
  and first setup; named operators are preferable everywhere else.

Missing or invalid token ⇒ `401`. Valid token whose role lacks the permission ⇒ `403`.

## Login
Exchange operator credentials for a time-limited session token.
Operator accounts are declared server-side; passwords are stored hashed, never in clear text.
#### HTTP API v1
POST /login
Request body : `{"login": "...", "password": "..."}`
Success response : `{"token", "login", "role", "permissions", "expiresAt"}`. Code : 200 OK
Failure response : 401 UNAUTHORIZED (unknown login or wrong password — the two are not distinguished)
#### API function(s)
from JordanServer

    login(
        login : String,
        password : String
    ) : JordanAdminSession
#### Authentication and roles
None — this is the endpoint that issues the token. The session expires after the server-side TTL
(`JORDAN_ADMIN_SESSION_TTL`, 12 h by default), after which calls using it return 401.

## Logout
Close the session token used for the call. The shared bootstrap token has no session to close.
#### HTTP API v1
POST /logout
Success response : 200 OK
#### API function(s)
from JordanServer

    logout(
    ) : void
#### Authentication and roles
`Authorization: Bearer <adminToken>` — any authenticated operator.

## Current identity
Who the token belongs to and what it is allowed to do — lets an active client show only the
actions the operator may perform.
#### HTTP API v1
GET /me
Success response : `{"login", "role", "permissions"}`. Code : 200 OK
#### API function(s)
from JordanServer

    current_identity(
    ) : JordanAdminIdentity
#### Authentication and roles
`Authorization: Bearer <adminToken>` — any authenticated operator.

## Add server
Start to follow/administrate clients/tasks hosted from this server.
#### API function(s)
    static add_server(
        url : Uri/String,
        [password : String,]
    ) : JordanServer
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).

## List clients
Reads clients registered on this server.
A client has one or several tasks.
A task may have one or several available actions.
A structured DTO (from json object) describes all the above,
according to the role of the authenticated user.
#### HTTP API v1
GET /clients
Success response : 200 OK
#### API function(s)
from JordanServer

    list_clients(
    ) : list<JordanClientInstance>
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `read` permission — roles viewer, operator, admin. 403 when the authenticated operator holds a role without it.

## List actions
Reads available actions for a client or a task.
A client/task may have one or several available actions.
A structured DTO (from json object) describes the above,
according to the role of the authenticated user.
#### HTTP API v1
GET {taskId}/actions
Success response : 200 OK
#### API function(s)
from JordanServer

    list_actions(
    ) : list<JordanActionDefinition>
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `read` permission — roles viewer, operator, admin. 403 when the authenticated operator holds a role without it.


## Send message
Program an action which will be executed by Passive Client.
The `author` of the message is the login carried by the admin token: the server overwrites any
`author` sent in the request body, so the field always names whoever was authenticated.
#### HTTP API v1
POST {taskId}/message
Success response message_id. Code : 201 CREATED
#### API function(s)
from JordanClientTask 

    send_message(
        message : JordanMessage
    ) : JordanSentMessage
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `send` permission — roles operator, admin. 403 when the authenticated operator holds a role without it.
#### Workflow
send_message()
SERVER_RECEIVED
get_message()
MESSAGE_DELIVERED
MESSAGE_ACKNOWLEDGED
MESSAGE_PROCESSED

## Read Messages
List the sent messages and their workflow state.
#### HTTP API v1
GET {taskId}/messages
GET {clientId}/messages
Success response : List<JordanMessage>. Code : 200 OK or 204 No Content if empty.
#### API function(s)
from JordanClientTask or JordanClientInstance

    get_messages(
    ) : list<JordanMessage>
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `read` permission — roles viewer, operator, admin. 403 when the authenticated operator holds a role without it.

## Read status
Get last statuses sent by the client/task.
#### HTTP API v1
GET {taskId}/status
Success response : List<JordanStatus>. Code : 200 OK, or 204 No Content if empty.
#### API function(s)
from JordanClientTask or JordanClientInstance

    read_status(
        [line_count : int]
    ) : list<JordanStatus>
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `read` permission — roles viewer, operator, admin. 403 when the authenticated operator holds a role without it.
#### Nice to have
search filters -> on server or client side ?

## Delete Task
Delete a task (including a client) and all information stored on this task.
This includes :
- child tasks
- status and messages for this task
- reference from client collection (if the taskId is a clientId)
- reference from parent task (the parent task removes the taskId from its child tasks)
#### HTTP API v1
DELETE {taskId}
Success response : 200 OK
#### API function(s)
from JordanClientInstance

    delete(taskId) : Void
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `delete` permission — roles admin. 403 when the authenticated operator holds a role without it.

## Delete All
Clear everything stored on the server.
#### HTTP API v1
DELETE /all
Success response : 200 OK
#### API function(s)
from JordanInstance

    deleteAll() : Void
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `delete` permission — roles admin. 403 when the authenticated operator holds a role without it.

## Generic Query
Returns information stored for an ID.
#### HTTP API v1
GET {id}
Success response : 200 OK, 204 No Content if ID does not exists
#### API function(s)
from JordanClientInstance

    genericQuery(id) : String
#### Authentication and roles
`Authorization: Bearer <adminToken>` — session token returned by Login, or the shared bootstrap token (`JORDAN_ADMIN_TOKEN`). 401 if the header is missing, if the token is unknown or expired, or if the server has no admin credential configured (the namespace fails closed).
Requires the `read` permission — roles viewer, operator, admin. 403 when the authenticated operator holds a role without it.


