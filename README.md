# Assignment 3 - SPL251  
## Emergency Service Platform (STOMP Protocol)

###  Authors  
- Maia Barber  
- Yuval Pariente  

---

##  Overview  
This project implements a fully functional emergency messaging platform using the STOMP protocol.  
It contains:

-  Java-based STOMP Server  
-  C++ STOMP Client with multi-threaded support  
-  TPC & Reactor server models  
-  Support for SUBSCRIBE, SEND, DISCONNECT, etc.

---

##  Project Structure
Assignment3/  
├── server/  
│   ├── src/  
│   ├── pom.xml  
│   └── ...  
├── client/  
│   ├── src/  
│   ├── include/  
│   ├── bin/  
│   ├── makefile  
│   └── ...  
├── events/ # sample JSON input files  
├── README.md  

---

##  Running the Project

###  Server (Java)

#### Compile:
```bash
cd server
mvn compile
```

#### Run:
```bash
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="tpc 7777"
```

*You can replace `tpc` with `reactor` to run the Reactor model.*

---

###  Client (C++)

#### Compile:
```bash
cd client
make
```

#### Run:
```bash
./bin/StompClient <host> <port>
```

For example:
```bash
./bin/StompClient 127.0.0.1 7777
```

---

###  events/ folder

The `events/` directory contains sample JSON files representing game-related events.
These can be used as input when sending messages from the client to the server.

---

##  Requirements

- Java 11+
- Maven 3.6+
- C++11 or later (g++, clang++)
- Make

---

##  Example STOMP Frame

```text
SEND
destination:/game/updates

{"event":"PlayerJoined","name":"Alice"}
^@
```

---

## ✅ Features

- User login/logout
- SUBSCRIBE/UNSUBSCRIBE to topics (games)
- SEND updates from event files
- Summary generation after logout
