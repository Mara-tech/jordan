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
    ) : JordanInstance
#### Authentication
TBD

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
TBD

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
TBD


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
TBD
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
TBD

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
TBD


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
TBD


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
TBD


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

## Add server
Start to follow/administrate clients/tasks hosted from this server.
#### API function(s)
    static add_server(
        url : Uri/String,
        [password : String,]
    ) : JordanServer
#### Authentication
TBD

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
TBD

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
TBD


## Send message
Program an action which will be executed by Passive Client.
#### HTTP API v1
POST {taskId}/message
Success response message_id. Code : 201 CREATED
#### API function(s)
from JordanClientTask 

    send_message(
        message : JordanMessage
    ) : JordanSentMessage
#### Authentication
TBD
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
#### Authentication
TBD

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
#### Authentication
TBD
#### Nice to have
search filters -> on server or client side ?




