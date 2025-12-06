package com.fileserver;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class ClientHandler extends Thread {

    private final Socket clientSocket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String username;

    private static final Map<String, List<String>> userFiles = new HashMap<>();
    private static final Map<String, String> fileVisibility = new HashMap<>();
    private static final Map<String, FileRequest> fileRequests = new HashMap<>();
    private static final Map<String, List<String>> userMessages = new HashMap<>();

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            dis = new DataInputStream(clientSocket.getInputStream());
            dos = new DataOutputStream(clientSocket.getOutputStream());

            // Authenticate client
            while (true) {
                dos.writeUTF("Enter your username: ");
                String usernameInput = dis.readUTF();
                if (Server.isClientConnected(usernameInput)) {
                    dos.writeUTF("Username already taken. Connection terminated.");
                    clientSocket.close();
                    return;
                } else {
                    this.username = usernameInput;
                    Server.addClient(username, clientSocket);
                    dos.writeUTF("Welcome, " + username + "!");
                    createUserDirectory(username);
                    userFiles.putIfAbsent(username, new ArrayList<>());
                    break;
                }
            }

            // Handle client requests
            while (true) {
                String command = dis.readUTF();
                handleCommand(command);
            }

        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientSocket);
        } finally {
            if (username != null) {
                Server.removeClient(username);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createUserDirectory(String username) {
        File userDir = new File("clients", username);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
    }

    private void handleCommand(String command) throws IOException {
        List<String> parts = parseCommand(command);
        String action = parts.get(0);

        switch (action) {
            case "list_clients":
                listClients();
                break;
            case "list_my_files":
                listMyFiles();
                break;
            case "list_public_files":
                if (parts.size() > 1) {
                    listPublicFiles(parts.get(1));
                } else {
                    dos.writeUTF("Usage: list_public_files <username>");
                }
                break;
            case "upload":
                if (parts.size() > 2) {
                    uploadFile(parts.get(1), parts.get(2), null);
                } else {
                    dos.writeUTF("Usage: upload <filepath> <public/private>");
                }
                break;
            case "upload_request":
                if (parts.size() > 2) {
                    uploadFile(parts.get(2), "public", parts.get(1));
                } else {
                    dos.writeUTF("Usage: upload_request <request_id> <filepath>");
                }
                break;
            case "download":
                if (parts.size() > 1) {
                    downloadFile(parts.get(1));
                } else {
                    dos.writeUTF("Usage: download <filename>");
                }
                break;
            case "request_file":
                if (parts.size() > 2) {
                    // Reconstruct description with spaces
                    int recipientEndIndex = command.indexOf(parts.get(2));
                    String description = command.substring(recipientEndIndex);
                    requestFile(parts.get(1), description);
                } else {
                    dos.writeUTF("Usage: request_file <recipient> <description>");
                }
                break;
            case "view_messages":
                viewMessages();
                break;
            case "view_history":
                viewHistory();
                break;
            // Other cases will be added here
            default:
                dos.writeUTF("Unknown command.");
        }
    }

    private List<String> parseCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean inQuotes = false;
        for (char c : command.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                    currentPart = new StringBuilder();
                }
            } else {
                currentPart.append(c);
            }
        }
        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }
        return parts;
    }

    private void listClients() throws IOException {
        StringBuilder clientList = new StringBuilder("All clients:\n");
        Set<String> allUsers = Server.getAllUsers();
        Map<String, Socket> connectedClients = Server.getConnectedClients();

        for (String user : allUsers) {
            if (connectedClients.containsKey(user)) {
                clientList.append("- ").append(user).append(" (online)\n");
            } else {
                clientList.append("- ").append(user).append(" (offline)\n");
            }
        }
        dos.writeUTF(clientList.toString());
    }

    private void listMyFiles() throws IOException {
        StringBuilder fileList = new StringBuilder("Your files:\n");
        List<String> files = userFiles.get(username);
        if (files != null) {
            for (String file : files) {
                fileList.append("- ").append(file).append(" (").append(fileVisibility.get(file)).append(")\n");
            }
        }
        dos.writeUTF(fileList.toString());
    }

    private void listPublicFiles(String targetUser) throws IOException {
        StringBuilder fileList = new StringBuilder("Public files of " + targetUser + ":\n");
        List<String> files = userFiles.get(targetUser);
        if (files != null) {
            for (String file : files) {
                if ("public".equalsIgnoreCase(fileVisibility.get(file))) {
                    fileList.append("- ").append(file).append("\n");
                }
            }
        }
        dos.writeUTF(fileList.toString());
    }

    private void uploadFile(String filePath, String visibility, String requestId) throws IOException {
        // Extract filename from the path (client sends full path)
        String fileName = new File(filePath).getName();
        
        // First, we need to receive file size from client
        dos.writeUTF("send_file_info");
        String fileInfo = dis.readUTF();
        
        if (fileInfo.startsWith("file_not_found")) {
            dos.writeUTF("File not found on client: " + filePath);
            return;
        }
        
        long fileSize = Long.parseLong(fileInfo);
        String fileId = UUID.randomUUID().toString();

        if (Server.getBufferSize() + fileSize > Server.MAX_BUFFER_SIZE) {
            dos.writeUTF("Server buffer is full. Cannot upload the file.");
            return;
        }

        int chunkSize = new Random().nextInt(Server.MAX_CHUNK_SIZE - Server.MIN_CHUNK_SIZE + 1) + Server.MIN_CHUNK_SIZE;
        dos.writeUTF("upload_ready " + chunkSize + " " + fileId);

        File userDir = new File("clients", username);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        
        try (FileOutputStream fos = new FileOutputStream("clients/" + username + "/" + fileId + ".tmp")) {
            long bytesRead = 0;
            while (bytesRead < fileSize) {
                int bytesToRead = (int) Math.min(chunkSize, fileSize - bytesRead);
                byte[] buffer = new byte[bytesToRead];
                int read = dis.read(buffer, 0, bytesToRead);
                if (read == -1) {
                    break;
                }
                fos.write(buffer, 0, read);
                bytesRead += read;
                dos.writeUTF("chunk_ok");
            }

            String completion = dis.readUTF();
            if (completion.equals("upload_complete")) {
                if (bytesRead == fileSize) {
                    File tmpFile = new File("clients/" + username + "/" + fileId + ".tmp");
                    File finalFile = new File("clients/" + username + "/" + fileName);
                    tmpFile.renameTo(finalFile);
                    dos.writeUTF("File uploaded successfully: " + fileName);
                    userFiles.get(username).add(fileName);
                    fileVisibility.put(fileName, visibility);
                    Logger.log(username, "upload", fileName, "successful");

                    if (requestId != null) {
                        handleRequestedFileUpload(requestId, fileName);
                    }
                } else {
                    dos.writeUTF("File upload failed: size mismatch.");
                    new File("clients/" + username + "/" + fileId + ".tmp").delete();
                    Logger.log(username, "upload", fileName, "failed");
                }
            }
        } catch (IOException e) {
            dos.writeUTF("File upload failed.");
            new File("clients/" + username + "/" + fileId + ".tmp").delete();
            Logger.log(username, "upload", fileName, "failed");
        }
    }

    private void downloadFile(String fileName) throws IOException {
        File file = new File("clients/" + username + "/" + fileName);
        if (!file.exists()) {
            // Check public files of other users
            boolean found = false;
            for (Map.Entry<String, List<String>> entry : userFiles.entrySet()) {
                if (!entry.getKey().equals(username)) {
                    for (String f : entry.getValue()) {
                        if (f.equals(fileName) && "public".equalsIgnoreCase(fileVisibility.get(f))) {
                            file = new File("clients/" + entry.getKey() + "/" + fileName);
                            found = true;
                            break;
                        }
                    }
                }
                if (found) break;
            }
        }

        if (file.exists()) {
            dos.writeUTF("download_ready " + file.length());
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[Server.MAX_CHUNK_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.writeUTF("download_complete");
                Logger.log(username, "download", fileName, "successful");
            }
        } else {
            dos.writeUTF("File not found.");
            Logger.log(username, "download", fileName, "failed");
        }
    }

    private void requestFile(String recipient, String description) throws IOException {
        String requestId = UUID.randomUUID().toString();
        FileRequest request = new FileRequest(requestId, description, username, recipient);
        fileRequests.put(requestId, request);

        if ("ALL".equalsIgnoreCase(recipient)) {
            for (String user : Server.getAllUsers()) {
                if (!user.equals(username)) {
                    addMessage(user, "New file request from " + username + " for '" + description + "'. Request ID: " + requestId);
                }
            }
        } else {
            addMessage(recipient, "New file request from " + username + " for '" + description + "'. Request ID: " + requestId);
        }
        dos.writeUTF("File request sent. Request ID: " + requestId);
    }

    private void viewMessages() throws IOException {
        List<String> messages = userMessages.get(username);
        if (messages == null || messages.isEmpty()) {
            dos.writeUTF("No new messages.");
        } else {
            StringBuilder sb = new StringBuilder("Your messages:\n");
            for (String msg : messages) {
                sb.append("- ").append(msg).append("\n");
            }
            dos.writeUTF(sb.toString());
            messages.clear();
        }
    }

    private void addMessage(String user, String message) {
        userMessages.putIfAbsent(user, new ArrayList<>());
        userMessages.get(user).add(message);
    }

    private void handleRequestedFileUpload(String requestId, String fileName) {
        FileRequest request = fileRequests.get(requestId);
        if (request != null) {
            addMessage(request.getRequester(), "File '" + fileName + "' has been uploaded for your request '" + request.getDescription() + "' by user " + username);
        }
    }

    private void viewHistory() throws IOException {
        File logFile = new File("clients/" + username + "/log.txt");
        if (logFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                StringBuilder history = new StringBuilder("Your history:\n");
                String line;
                while ((line = reader.readLine()) != null) {
                    history.append(line).append("\n");
                }
                dos.writeUTF(history.toString());
            }
        } else {
            dos.writeUTF("No history found.");
        }
    }
}
