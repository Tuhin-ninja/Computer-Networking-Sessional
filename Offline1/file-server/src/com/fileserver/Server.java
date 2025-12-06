
package com.fileserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Server {

    public static final int PORT = 5001;
    public static final String SERVER_FILES_DIR = "server_files";
    public static final int MAX_BUFFER_SIZE = 1024 * 1024 * 100; // 100 MB
    public static final int MIN_CHUNK_SIZE = 1024 * 10; // 10 KB
    public static final int MAX_CHUNK_SIZE = 1024 * 100; // 100 KB

    private static final Map<String, Socket> connectedClients = new HashMap<>();
    private static final Set<String> allUsers = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("Starting File Server...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }

    public static synchronized boolean isClientConnected(String username) {
        return connectedClients.containsKey(username);
    }

    public static synchronized void addClient(String username, Socket socket) {
        connectedClients.put(username, socket);
        allUsers.add(username);
    }

    public static synchronized void removeClient(String username) {
        connectedClients.remove(username);
    }

    private static long bufferSize = 0;

    public static synchronized long getBufferSize() {
        return bufferSize;
    }

    public static synchronized void addToBuffer(long size) {
        bufferSize += size;
    }

    public static synchronized void removeFromBuffer(long size) {
        bufferSize -= size;
    }

    public static synchronized Map<String, Socket> getConnectedClients() {
        return new HashMap<>(connectedClients);
    }

    public static synchronized Set<String> getAllUsers() {
        return new HashSet<>(allUsers);
    }
}
