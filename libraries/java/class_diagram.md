# Passive Client

## JordanTask <- JordanInstance
- sendStatus()
- newTask()
- complete()
- readMessage()
- taskId
- name

### en plus dans JordanInstance
- unregister()
- clientId
- name

## JordanActionDefinition
- name
- parameters : list<JordanActionParameter>

## JordanActionParameter
- name
- type

## JordanAction
- definition : JordanActionDefinition
- placeholders : list<JordanActionPlaceholder> 

## JordanActionPlaceholder<T>
- value : T

## JordanStatus <- JordanSuccessStatus
- type : String
- status : String (possible extension to custom serializable object)
- timestamp
- taskId

## JordanMessage
- messageId
- author
- action : JordanAction
- stateAudit : list<JordanMessageState>
- acknowledge()
- processed()

## JordanMessageState
- timestamp
- state : JordanMessageStateEnum

## JordanMessageStateEnum
SERVER_RECEIVED, MESSAGE_DELIVERED, MESSAGE_ACKNOWLEDGED, MESSAGE_PROCESSED, 
ERROR_MESSAGE_NOT_DELIVERED, ERROR_CANNOT_PROCESS_MESSAGE, ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER

# Active Client

## JordanServer
- listClients()

## JordanClientInstance
- clientId
- name
- state
- tasks : list<JordanClientTask>

## JordanClientTask     (<- JordanClientInstance ? or common base class for some implementations)
- taskId
- name
- state
- actions : list<JordanActionDefinition>
