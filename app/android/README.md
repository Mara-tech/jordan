# Android App 
This Android app is a client to interact with programs having registered to a Jordan server.
In other words, with a few lines of code in any program (aiming long-time execution), this is :
- a generic GUI
- On your personal/professional Smartphone
- Anywhere (LAN/Internet), according to Jordan server access



## Jordan Server list
Add the base URI of a Jordan server API.

<p align="center">
    <img src="data/server_list.png" 
          alt="Jordan Server List screenshot" 
          height="600"/>
</p>
Here 3 servers are added and saved by the user.

## Authentication

The `/jordan/admin/*` endpoints the app calls require an operator session token.

- **Login** — the app exchanges a login and a password for a session token through
  `POST /jordan/admin/login`, and sends it as `Authorization: Bearer <token>` on every following
  call. One session per server: switching servers does not reuse a token.
- **Two separate dialogs** — *Server setup* declares a server (name, URL, and a « Try » button
  that checks it answers on `GET /hello`); the *Server login* dialog is the only place where
  credentials are typed, and the only one that actually verifies them.
- **Credentials** — ticking *Remember these credentials on this device* in the login dialog saves
  them for that server, which then opens its session on its own when you enter it. Unticking the
  box on the next login erases what was saved.
- **On demand** — *Log in* / *Log out* are available in the overflow menu of the client screens.
- **When the server refuses a call** (`401`, no session or an expired one), the app asks for the
  credentials instead of showing a network error, and reloads the screen once logged in.
- **Roles** — a `403` means the operator role is too narrow for the action (`viewer` reads,
  `operator` also sends messages, `admin` also deletes). Logging in again does not widen it.

The session token lives in memory only: closing the app closes the session on this device.

## Jordan Client Interactions
A server may have one or several clients.
These clients are the executing program that has *register*ed.
User can interact with a client in different forms

### Status
Client (executing program) may send status.
The status purpose is definitely to let the user know how and where the execution is.
It may be considered as logs, dedicated to take actions from a Jordan User Interface (such as this Android app).
<p align="center">
    <img src="data/status.png" 
          alt="Jordan Client status screenshot" 
          height="600"/>
</p>
These statuses may help the user to decide if an action should be taken.

### Actions
This is the central part of Interactions in Jordan.
The user is able to send a message back to the client so the program may act in consequence.
The client define possible actions, when registering to the Jordan Server, 
and handle messages (an action executed by the user).

<p align="center">
    <img src="data/actions.png" 
          alt="Jordan Actions screenshot" 
          height="600"/>
</p>
 
 ### Messages
 Eventually, here are the messages sent to the client.
 This is the feedback of your actions (and perhaps from other users).
 A state associated to each message tells where it is in the workflow, e.g :
 1. Server received
 2. Delivered to client
 3. Client acknowledges
 4. Message complete
 5. or in the contrary, Message failure
 
 <p align="center">
    <img src="data/messages_state.png" 
          alt="Jordan messages screenshot" 
          height="600"/>
</p>

