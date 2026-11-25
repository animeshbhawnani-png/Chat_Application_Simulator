# Chat_Application_Simulator

 📄 Project Report & PPT Submission
The project documentation and presentation are available through Google Drive:
- Project Report & Presentation:https://drive.google.com/drive/folders/1GPxCMlihcKomJ6VsGWsHboxQweUtLQDG?usp=sharing 

VibeChat — Chat Application Simulator
A local, browser-based chat application simulator built with Core Java, HTML, CSS, and vanilla JavaScript. Its Instagram-inspired purple–pink gradient interface provides a familiar direct-messaging experience, while the Java backend demonstrates fundamental Data Structures and Algorithms (DSA).
Note: VibeChat is an educational, local-only simulator. It is not affiliated with Instagram and does not send messages over the internet.

Features
- Register users with unique IDs and search for them.
- Select an active user and open one-to-one conversations.
- Send messages and view conversation history in chat bubbles.
- See pending messages, deliver the oldest message, or undo the latest undelivered message through Chat actions.
- Search stored messages by keyword.
- Save registered users and conversation history locally and restore them after restarting.
- Use a responsive interface on desktop and mobile.
DSA Concepts Used
Data structure	Purpose
HashMap<String, User>	Register and look up users by ID.
LinkedList<Message>	Maintain ordered messages in each conversation.
Queue<Message>	Deliver pending messages in FIFO (first-in, first-out) order.
Stack<Message>	Undo the latest eligible undelivered message in LIFO order.
File handling	Persist users and conversation history in chat_messages.txt.


The data-structure operations run in Java, not in the browser. The frontend calls a lightweight local HTTP bridge.
Tech Stack
- Backend: Core Java (Java 21-compatible)
- HTTP server: JDK com.sun.net.httpserver.HttpServer
- Frontend: HTML5, CSS3, vanilla JavaScript
- Communication: Fetch API and JSON responses
- Storage: Local text file (Base64-encoded fields)
- Tools: VS Code and a modern web browser
No Spring Boot, database, Maven, Gradle, or external JavaScript frameworks are required.
Project Structure
ChatApplicationSim-CoreJava/
├── src/
│   ├── Main.java             # Original console app and DSA operations
│   └── ChatWebServer.java    # Local HTTP bridge for the browser UI
├── frontend/
│   ├── index.html            # Messaging interface
│   ├── style.css             # Instagram-inspired visual styling
│   └── script.js             # UI interactions and API requests
├── out/                      # Generated compiled classes (after build)
└── chat_messages.txt         # Generated local save file (after use)
out/ and chat_messages.txt are runtime artifacts and do not need to be committed to GitHub.
How to Run
Requirements: JDK 21 or newer, VS Code or a terminal, and a modern browser.
1. Clone or download this repository and open its root folder in VS Code.
2. Open Terminal → New Terminal.
3. Compile from the project root:
   javac --release 21 -d out src/Main.java src/ChatWebServer.java
4. Start the backend:
   java -cp out ChatWebServer
5. Open http://127.0.0.1:8081 in your browser.
6. Keep the terminal running while using the app. Press Ctrl+C to stop the server.
If port 8081 is already occupied, use:
java -cp out ChatWebServer --port=8082
Then open http://127.0.0.1:8082.

Important: Run ChatWebServer to use the browser interface. Running java -cp out Main opens the original console-based menu instead. Do not run both versions simultaneously against the same save file.
Quick Demo / Manual Test
For a fresh data file:
1. Register U101 (Aman) and U102 (Riya).
2. Select Aman as the active user and open Riya's conversation.
3. Send three messages; the IDs should be M101, M102, and M103.
4. Open Chat actions and verify the pending-message order is M101 → M102 → M103.
5. Select Deliver next; M101 leaves the pending queue but remains in history.
6. Select Undo last unsent; M103 disappears from the pending queue and conversation history.
7. Search for text from M101 to verify that delivered messages remain searchable.
8. Stop and restart the server: users and history return, while the pending queue and undo stack start empty.
If you already have saved data, user IDs and generated message numbers may differ.
Local REST API
Method	Endpoint	Action
GET	/api/health	Health check
GET	/api/users	List registered users
POST	/api/users	Register a user
GET	/api/users/search?id=U101	Search by user ID
POST	/api/messages	Queue a message
GET	/api/conversations?user1Id=U101&user2Id=U102	View a conversation
GET	/api/messages/pending	List pending messages
POST	/api/messages/deliver-next	Deliver oldest pending message
POST	/api/messages/undo	Undo latest undelivered message
GET	/api/messages/search?keyword=hello	Search message history


The POST endpoints that require fields use application/x-www-form-urlencoded request bodies. Responses use JSON. The frontend handles these requests automatically.
Data Storage and Reset
The app saves users and conversation history to chat_messages.txt in the project root. It uses Base64-encoded fields to preserve text safely. The server saves after changes and on normal shutdown.

To start again with empty data:
1. Stop the server completely (Ctrl+C).
2. Back up or rename chat_messages.txt.
3. Restart ChatWebServer and refresh the browser.
Do not delete the save file while the server is running; the running application may write its current data back to disk.
Limitations
- Local educational simulation, not internet or real-time messaging.
- No authentication, account passwords, online presence, read receipts, attachments, or group chats.
- Pending delivery and undo state are held in memory and do not survive a restart.
- The browser must be opened through the Java server; opening frontend/index.html directly as a file:// page will not connect to the API.
Suggested .gitignore
out/
*.class
chat_messages.txt
chat_messages_backup*.txt
*.tmp
.vscode/
License
No license has been specified yet. Add a LICENSE file if you want to explicitly permit others to reuse the code.
