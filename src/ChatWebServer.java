import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Small HTTP adapter for the user's ORIGINAL console-based Main.java.
 * Main.java is unchanged. This adapter shares its HashMap, LinkedList, Queue and Stack.
 * Run from the project root: java -cp out ChatWebServer
 */
public class ChatWebServer {
    private static final Object LOCK = new Object();
    private static final int BODY_LIMIT = 64 * 1024;
    private static final Path FRONTEND = Path.of("frontend").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        int port = 8081;
        for (String arg : args) {
            if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        synchronized (LOCK) { Main.loadData(); }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", ChatWebServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (LOCK) {
                try { saveSafely(); System.out.println("Chat data saved on shutdown."); }
                catch (IOException e) { System.err.println("Save failed: " + e.getMessage()); }
            }
        }));
        server.start();
        System.out.println("Instagram-style Chat Simulator running at http://127.0.0.1:" + port);
        System.out.println("Press Ctrl+C to save and stop.");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String route = exchange.getRequestURI().getPath();
            if (!route.startsWith("/api/")) { serveFile(exchange, route); return; }
            try {
                synchronized (LOCK) { api(exchange, route); }
            } catch (BadRequest e) {
                writeJson(exchange, e.status, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                System.err.println("API error " + route + ": " + e);
                writeJson(exchange, 500, Map.of("error", "Server error. See the Java terminal."));
            }
        }
    }

    private static void api(HttpExchange x, String route) throws IOException {
        String method = x.getRequestMethod();
        Map<String, String> data;
        switch (route) {
            case "/api/health":
                checkMethod(method, "GET");
                writeJson(x, 200, Map.of("status", "ok")); return;
            case "/api/users":
                if ("GET".equals(method)) { writeJson(x, 200, users()); return; }
                checkMethod(method, "POST"); data = form(x);
                String id = required(data, "id").toUpperCase(Locale.ROOT);
                String name = required(data, "name");
                if (Main.users.containsKey(id)) throw new BadRequest(409, "User ID already exists.");
                Main.users.put(id, new User(id, name));
                saveOrThrow();
                writeJson(x, 201, Map.of("id", id, "name", name, "message", "User registered")); return;
            case "/api/users/search":
                checkMethod(method, "GET");
                User u = Main.findUser(required(query(x), "id"));
                if (u == null) throw new BadRequest(404, "User not found.");
                writeJson(x, 200, user(u)); return;
            case "/api/messages":
                checkMethod(method, "POST"); data = form(x);
                String sender = required(data, "senderId").toUpperCase(Locale.ROOT);
                String receiver = required(data, "receiverId").toUpperCase(Locale.ROOT);
                String text = required(data, "text");
                if (Main.findUser(sender) == null) throw new BadRequest(400, "Sender is not registered.");
                if (Main.findUser(receiver) == null) throw new BadRequest(400, "Receiver is not registered.");
                if (sender.equals(receiver)) throw new BadRequest(400, "You cannot message yourself.");
                Message message = new Message("M" + Main.messageCount++, sender, receiver, text);
                Main.getChat(sender, receiver).messages.addLast(message);
                Main.outgoing.add(message);
                Main.undoStack.push(message);
                saveOrThrow();
                Map<String, Object> sent = message(message);
                sent.put("message", "Message queued");
                writeJson(x, 201, sent); return;
            case "/api/conversations":
                checkMethod(method, "GET"); data = query(x);
                String a = required(data, "user1Id").toUpperCase(Locale.ROOT);
                String b = required(data, "user2Id").toUpperCase(Locale.ROOT);
                if (Main.findUser(a) == null || Main.findUser(b) == null)
                    throw new BadRequest(400, "Both users must be registered.");
                Chat chat = Main.findChat(a, b);
                List<Map<String, Object>> history = new ArrayList<>();
                if (chat != null) for (Message m : chat.messages) history.add(message(m));
                writeJson(x, 200, history); return;
            case "/api/messages/pending":
                checkMethod(method, "GET");
                List<Map<String, Object>> pending = new ArrayList<>();
                for (Message m : Main.outgoing) pending.add(message(m));
                writeJson(x, 200, pending); return;
            case "/api/messages/deliver-next":
                checkMethod(method, "POST");
                Message delivered = Main.outgoing.poll();
                if (delivered == null) { writeJson(x, 200, Map.of("message", "No messages to deliver.")); return; }
                Main.undoStack.remove(delivered);
                saveOrThrow();
                Map<String, Object> deliveredResult = message(delivered);
                deliveredResult.put("message", "Message delivered successfully");
                writeJson(x, 200, deliveredResult); return;
            case "/api/messages/undo":
                checkMethod(method, "POST");
                if (Main.undoStack.isEmpty()) {
                    writeJson(x, 200, Map.of("message", "No unsent messages to undo.")); return;
                }
                Message removed = Main.undoStack.pop();
                Main.outgoing.remove(removed);
                Chat containing = Main.findChat(removed.sender, removed.receiver);
                if (containing != null) containing.messages.remove(removed);
                saveOrThrow();
                writeJson(x, 200, Map.of("message", "Last unsent message undone", "messageId", removed.id)); return;
            case "/api/messages/search":
                checkMethod(method, "GET");
                String keyword = required(query(x), "keyword").toLowerCase(Locale.ROOT);
                List<Map<String, Object>> found = new ArrayList<>();
                for (Chat c : Main.chats) for (Message m : c.messages)
                    if (m.text.toLowerCase(Locale.ROOT).contains(keyword)) found.add(message(m));
                writeJson(x, 200, found); return;
            default: throw new BadRequest(404, "Endpoint not found.");
        }
    }

    private static List<Map<String, Object>> users() {
        List<User> list = new ArrayList<>(Main.users.values());
        list.sort(Comparator.comparing(u -> u.id));
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : list) result.add(user(u));
        return result;
    }
    private static Map<String, Object> user(User u) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", u.id); result.put("name", u.name); return result;
    }
    private static Map<String, Object> message(Message m) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", m.id);
        result.put("senderId", m.sender);
        result.put("receiverId", m.receiver);
        result.put("text", m.text);
        return result;
    }

    // Keep the exact USER|base64 / MESSAGE|base64 format from the original Main.java.
    // Save on each mutation, using a temporary file so a partial write cannot replace data.
    private static void saveSafely() throws IOException {
        Path target = Path.of(Main.FILE_NAME).toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), "chat-web-", ".tmp");
        try {
            List<String> lines = new ArrayList<>();
            for (User u : Main.users.values())
                lines.add("USER|" + Main.encode(u.id) + "|" + Main.encode(u.name));
            for (Chat c : Main.chats) for (Message m : c.messages)
                lines.add("MESSAGE|" + Main.encode(m.id) + "|" + Main.encode(m.sender)
                        + "|" + Main.encode(m.receiver) + "|" + Main.encode(m.text));
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
    private static void saveOrThrow() throws IOException { saveSafely(); }

    private static Map<String, String> query(HttpExchange x) {
        return parseForm(x.getRequestURI().getRawQuery());
    }
    private static Map<String, String> form(HttpExchange x) throws IOException {
        String type = x.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded"))
            throw new BadRequest(415, "Expected form-encoded request.");
        byte[] bytes = x.getRequestBody().readNBytes(BODY_LIMIT + 1);
        if (bytes.length > BODY_LIMIT) throw new BadRequest(413, "Request is too large.");
        return parseForm(new String(bytes, StandardCharsets.UTF_8));
    }
    private static Map<String, String> parseForm(String raw) {
        Map<String, String> fields = new HashMap<>();
        if (raw == null || raw.isBlank()) return fields;
        try {
            for (String part : raw.split("&", -1)) {
                String[] pair = part.split("=", 2);
                String k = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String v = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                if (fields.putIfAbsent(k, v) != null) throw new BadRequest(400, "Duplicate parameter: " + k);
            }
        } catch (IllegalArgumentException e) {
            if (e instanceof BadRequest b) throw b;
            throw new BadRequest(400, "Invalid query or form encoding.");
        }
        return fields;
    }
    private static String required(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) throw new BadRequest(400, key + " is required.");
        return value.trim();
    }
    private static void checkMethod(String actual, String expected) {
        if (!expected.equals(actual)) throw new BadRequest(405, "Method not allowed: use " + expected);
    }
    private static void serveFile(HttpExchange x, String route) throws IOException {
        if (!x.getRequestMethod().equals("GET")) {
            writeJson(x, 405, Map.of("error", "GET required.")); return;
        }
        String name = switch (route) {
            case "/", "/index.html" -> "index.html";
            case "/style.css" -> "style.css";
            case "/script.js" -> "script.js";
            default -> null;
        };
        if (name == null) { writeJson(x, 404, Map.of("error", "Not found.")); return; }
        Path file = FRONTEND.resolve(name);
        if (!Files.isRegularFile(file)) { writeJson(x, 404, Map.of("error", "Frontend file missing.")); return; }
        byte[] bytes = Files.readAllBytes(file);
        String type = name.endsWith(".html") ? "text/html" : name.endsWith(".css") ? "text/css" : "text/javascript";
        x.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(200, bytes.length);
        x.getResponseBody().write(bytes);
    }
    private static void writeJson(HttpExchange x, int status, Object body) throws IOException {
        byte[] bytes = json(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> m) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) parts.add(quote(e.getKey().toString()) + ":" + json(e.getValue()));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> parts = new ArrayList<>();
            for (Object item : items) parts.add(json(item));
            return "[" + String.join(",", parts) + "]";
        }
        throw new IllegalArgumentException("Cannot serialize JSON value");
    }
    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int)c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
    private static final class BadRequest extends IllegalArgumentException {
        final int status;
        BadRequest(int status, String text) { super(text); this.status = status; }
    }
}
