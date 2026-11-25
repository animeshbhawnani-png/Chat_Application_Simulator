
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Stores user details
class User {
    String id, name;

    User(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

// Stores message details
class Message {
    String id, sender, receiver, text;

    Message(String id, String sender, String receiver, String text) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.text = text;
    }
}

// Stores conversation between two users
class Chat {
    String user1, user2;

    // LinkedList stores conversation messages
    LinkedList<Message> messages = new LinkedList<>();

    Chat(String u1, String u2) {
        user1 = u1;
        user2 = u2;
    }
}

public class Main {

    static Scanner sc = new Scanner(System.in);

    // HashMap stores users using unique IDs
    static HashMap<String, User> users = new HashMap<>();

    // Stores all conversations
    static ArrayList<Chat> chats = new ArrayList<>();

    // Queue stores outgoing messages (FIFO)
    static Queue<Message> outgoing = new LinkedList<>();

    // Stack stores unsent messages for undo (LIFO)
    static Stack<Message> undoStack = new Stack<>();

    static int messageCount = 101;

    static final String FILE_NAME = "chat_messages.txt";

    // Find user using HashMap
    static User findUser(String id) {
        return users.get(id.trim().toUpperCase());
    }

    // Register a new user
    static void addUser() {

        System.out.print("Enter User ID: ");
        String id = sc.nextLine().trim().toUpperCase();

        if (id.isEmpty()) {
            System.out.println("User ID cannot be empty!");
            return;
        }

        if (users.containsKey(id)) {
            System.out.println("User ID already exists!");
            return;
        }

        System.out.print("Enter User Name: ");
        String name = sc.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Name cannot be empty!");
            return;
        }

        users.put(id, new User(id, name));

        System.out.println("User registered successfully!");
    }

    // Search user using HashMap
    static void searchUser() {

        System.out.print("Enter User ID: ");
        String id = sc.nextLine().trim();

        User u = findUser(id);

        if (u == null) {
            System.out.println("User " + id.toUpperCase() + " not found.");
        } else {
            System.out.println("User Found!");
            System.out.println("ID: " + u.id);
            System.out.println("Name: " + u.name);
        }
    }

    // Display all users
    static void displayUsers() {

        if (users.isEmpty()) {
            System.out.println("No users registered!");
            return;
        }

        System.out.println("\nRegistered Users:");

        for (User u : users.values()) {
            System.out.println(u.id + " - " + u.name);
        }
    }

    // Find an existing conversation
    static Chat findChat(String u1, String u2) {

        for (Chat c : chats) {

            if ((c.user1.equals(u1) && c.user2.equals(u2)) ||
                (c.user1.equals(u2) && c.user2.equals(u1))) {

                return c;
            }
        }

        return null;
    }

    // Create a unique conversation key
    static String chatKey(String u1, String u2) {

        if (u1.compareTo(u2) < 0)
            return u1 + "_" + u2;

        return u2 + "_" + u1;
    }

    // Get existing chat or create a new one
    static Chat getChat(String u1, String u2) {

        Chat c = findChat(u1, u2);

        if (c == null) {
            c = new Chat(u1, u2);
            chats.add(c);
        }

        return c;
    }

    // Send a message
    static void sendMessage() {

        System.out.print("Enter Sender ID: ");
        String sender = sc.nextLine().trim().toUpperCase();

        System.out.print("Enter Receiver ID: ");
        String receiver = sc.nextLine().trim().toUpperCase();

        // Check whether sender exists
        if (findUser(sender) == null) {
            System.out.println("User " + sender + " not found. Message not sent.");
            return;
        }

        // Check whether receiver exists
        if (findUser(receiver) == null) {
            System.out.println("User " + receiver + " not found. Message not sent.");
            return;
        }

        if (sender.equals(receiver)) {
            System.out.println("Cannot send message to yourself!");
            return;
        }

        System.out.print("Enter Message: ");
        String text = sc.nextLine().trim();

        if (text.isEmpty()) {
            System.out.println("Message cannot be empty!");
            return;
        }

        // Generate unique message ID
        String id = "M" + messageCount++;

        // Create message object
        Message m = new Message(id, sender, receiver, text);

        // Find or create conversation
        Chat c = getChat(sender, receiver);

        // Add message to conversation LinkedList
        c.messages.add(m);

        // Add message to outgoing Queue
        outgoing.add(m);

        // Push message onto Stack
        undoStack.push(m);

        System.out.println("Message queued: " + id + " | "
                + findUser(sender).name + " -> "
                + findUser(receiver).name);
    }

    // Deliver next message from Queue
    static void deliverNext() {

        if (outgoing.isEmpty()) {
            System.out.println("No messages to deliver.");
            return;
        }

        // Remove first message from Queue
        Message m = outgoing.poll();

        // Remove delivered message from undo Stack
        undoStack.remove(m);

        System.out.println("Message Delivered: " + m.id);
        System.out.println(findUser(m.sender).name + " -> "
                + findUser(m.receiver).name + ": " + m.text);
    }

    // View conversation history
    static void viewConversation() {

        System.out.print("Enter First User ID: ");
        String u1 = sc.nextLine().trim().toUpperCase();

        System.out.print("Enter Second User ID: ");
        String u2 = sc.nextLine().trim().toUpperCase();

        if (findUser(u1) == null || findUser(u2) == null) {
            System.out.println("Invalid user!");
            return;
        }

        Chat c = findChat(u1, u2);

        if (c == null || c.messages.isEmpty()) {
            System.out.println("No conversation found!");
            return;
        }

        System.out.println("\nConversation History:");

        for (Message m : c.messages) {
            System.out.println(m.id + " | "
                    + findUser(m.sender).name + " -> "
                    + findUser(m.receiver).name + ": "
                    + m.text);
        }
    }

    // Undo latest unsent message
    static void undoMessage() {

        if (undoStack.isEmpty()) {
            System.out.println("No unsent message to undo!");
            return;
        }

        // Pop latest message from Stack
        Message m = undoStack.pop();

        // Remove message from outgoing Queue
        outgoing.remove(m);

        // Remove message from conversation history
        Chat c = findChat(m.sender, m.receiver);

        if (c != null) {
            c.messages.remove(m);
        }

        System.out.println("Last message removed from outgoing queue/history.");
    }

    // Display outgoing messages
    static void showOutgoing() {

        if (outgoing.isEmpty()) {
            System.out.println("No outgoing messages!");
            return;
        }

        System.out.println("\nOutgoing Messages:");

        for (Message m : outgoing) {
            System.out.println(m.id + " | "
                    + findUser(m.sender).name + " -> "
                    + findUser(m.receiver).name + ": "
                    + m.text);
        }
    }

    // Search message content
    static void searchMessage() {

        System.out.print("Enter keyword: ");
        String word = sc.nextLine().trim().toLowerCase();

        if (word.isEmpty()) {
            System.out.println("Enter a valid keyword!");
            return;
        }

        boolean found = false;

        for (Chat c : chats) {

            for (Message m : c.messages) {

                if (m.text.toLowerCase().contains(word)) {

                    System.out.println(m.id + " | "
                            + findUser(m.sender).name + " -> "
                            + findUser(m.receiver).name + ": "
                            + m.text);

                    found = true;
                }
            }
        }

        if (!found) {
            System.out.println("No matching messages found!");
        }
    }

    // Encode text before saving to file
    static String encode(String text) {
        return Base64.getEncoder().encodeToString(
                text.getBytes(StandardCharsets.UTF_8));
    }

    // Decode text after reading from file
    static String decode(String text) {
        return new String(
                Base64.getDecoder().decode(text),
                StandardCharsets.UTF_8);
    }

    // Save users and conversation history
    static void saveData() {

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(FILE_NAME))) {

            // Save all registered users
            for (User u : users.values()) {
                pw.println("USER|" + encode(u.id) + "|"
                        + encode(u.name));
            }

            // Save all conversation messages
            for (Chat c : chats) {

                for (Message m : c.messages) {

                    pw.println("MESSAGE|" + encode(m.id) + "|"
                            + encode(m.sender) + "|"
                            + encode(m.receiver) + "|"
                            + encode(m.text));
                }
            }

            System.out.println("Data saved successfully!");

        } catch (IOException e) {
            System.out.println("Error saving data!");
        }
    }

    // Load users and conversation history at startup
    static void loadData() {

        File file = new File(FILE_NAME);

        if (!file.exists()) {
            System.out.println("New chat application started.");
            return;
        }

        ArrayList<String> messageLines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new FileReader(file))) {

            String line;

            // First load all users
            while ((line = br.readLine()) != null) {

                String[] data = line.split("\\|", -1);

                try {
                    if (data.length == 3 && data[0].equals("USER")) {

                        String id = decode(data[1]);
                        String name = decode(data[2]);

                        users.put(id, new User(id, name));
                    }

                    // Store message lines for second pass
                    else if (data.length == 5 &&
                             data[0].equals("MESSAGE")) {

                        messageLines.add(line);
                    }

                } catch (IllegalArgumentException e) {
                    System.out.println("Invalid data line skipped.");
                }
            }

            // Then load all conversation messages
            for (String msgLine : messageLines) {

                String[] data = msgLine.split("\\|", -1);

                try {
                    String id = decode(data[1]);
                    String sender = decode(data[2]);
                    String receiver = decode(data[3]);
                    String text = decode(data[4]);

                    // Only load messages with valid users
                    if (findUser(sender) == null ||
                        findUser(receiver) == null) {
                        continue;
                    }

                    Message m = new Message(
                            id, sender, receiver, text);

                    Chat c = getChat(sender, receiver);
                    c.messages.add(m);

                    // Update next message number
                    if (id.startsWith("M")) {
                        try {
                            int number = Integer.parseInt(
                                    id.substring(1));

                            if (number >= messageCount) {
                                messageCount = number + 1;
                            }

                        } catch (NumberFormatException e) {
                            // Ignore invalid message ID number
                        }
                    }

                } catch (IllegalArgumentException e) {
                    System.out.println("Invalid message skipped.");
                }
            }

            System.out.println("Previous data loaded successfully!");

        } catch (IOException e) {
            System.out.println("Error loading data!");
        }
    }

    // Main method
    public static void main(String[] args) {

        // Load previous data when application starts
        loadData();

        int choice = 0;

        do {

            System.out.println("\n========== CHAT APPLICATION ==========");
            System.out.println("1. Register User");
            System.out.println("2. Search User");
            System.out.println("3. Display All Users");
            System.out.println("4. Send Message");
            System.out.println("5. Deliver Next Message");
            System.out.println("6. View Conversation");
            System.out.println("7. Undo Last Unsent Message");
            System.out.println("8. Display Outgoing Messages");
            System.out.println("9. Search Message");
            System.out.println("10. Exit");

            System.out.print("\nEnter your choice: ");

            try {

                choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {

                    case 1:
                        addUser();
                        break;

                    case 2:
                        searchUser();
                        break;

                    case 3:
                        displayUsers();
                        break;

                    case 4:
                        sendMessage();
                        break;

                    case 5:
                        deliverNext();
                        break;

                    case 6:
                        viewConversation();
                        break;

                    case 7:
                        undoMessage();
                        break;

                    case 8:
                        showOutgoing();
                        break;

                    case 9:
                        searchMessage();
                        break;

                    case 10:
                        saveData();
                        System.out.println("Thank you for using Chat Application!");
                        break;

                    default:
                        System.out.println("Invalid choice!");
                }

            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number!");
            }

        } while (choice != 10);

        sc.close();
    }
}
