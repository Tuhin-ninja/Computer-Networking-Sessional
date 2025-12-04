package com.fileserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", Server.PORT);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to server.");

            // Handle server messages in a separate thread
            final String[] lastCommand = {""};
            new Thread(() -> {
                try {
                    while (true) {
                        String message = dis.readUTF();
                        if (message.startsWith("send_file_info")) {
                            if (lastCommand[0].startsWith("upload")) {
                                List<String> commandParts = parseCommand(lastCommand[0]);
                                System.out.println("Server requested file info for: " + commandParts.get(1));
                                handleFileInfo(dos, commandParts.get(1));
                            }
                        } else if (message.startsWith("upload_ready")) {
                            if (lastCommand[0].startsWith("upload")) {
                                List<String> commandParts = parseCommand(lastCommand[0]);
                                handleUploadRequest(message, dos, dis, commandParts.get(1));
                            }
                        } else if (message.startsWith("download_ready")) {
                            handleDownload(message, dis, lastCommand[0]);
                        } else {
                            System.out.println("Server: " + message);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            }).start();

            // Send user input to the server
            while (true) {
                String input = scanner.nextLine();
                lastCommand[0] = input;
                dos.writeUTF(input);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleFileInfo(DataOutputStream dos, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            dos.writeUTF("file_not_found");
        } else {
            dos.writeUTF(String.valueOf(file.length()));
        }
    }

    private static void handleUploadRequest(String serverMessage, DataOutputStream dos, DataInputStream dis, String filePath) throws IOException {
        String[] parts = serverMessage.split(" ");
        int chunkSize = Integer.parseInt(parts[1]);

        File file = new File(filePath);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                dis.readUTF(); // Wait for chunk_ok
            }
            dos.writeUTF("upload_complete");
            System.out.println("Server: " + dis.readUTF());
        }
    }

    private static void handleDownload(String serv