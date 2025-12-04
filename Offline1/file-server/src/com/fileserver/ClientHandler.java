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
          