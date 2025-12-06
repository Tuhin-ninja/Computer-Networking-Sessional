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
        try (Socket socket = new Socket("localhost", Server.PORT); DataInputStream dis = new DataInputStream(socket.getInputStream()); DataOutputStream dos = new DataOutputStream(socket.getOutputStream()); Scanner scanner = new Scanner(System.in)) {

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
                                String fileArg;
                                if (commandParts.get(0).equals("upload_request") && commandParts.size() > 2) {
                                    fileArg = commandParts.get(2); // upload_request <requestId> <filepath>
                                } else {
                                    fileArg = commandParts.get(1); // upload <filepath> <visibility>
                                }
                                System.out.println("Server requested file info for: " + fileArg);
                                handleFileInfo(dos, fileArg);
                            }
                        } else if (message.startsWith("upload_ready")) {
                            if (lastCommand[0].startsWith("upload")) {
                                List<String> commandParts = parseCommand(lastCommand[0]);
                                String fileArg;
                                if (commandParts.get(0).equals("upload_request") && commandParts.size() > 2) {
                                    fileArg = commandParts.get(2);
                                } else {
                                    fileArg = commandParts.get(1);
                                }
                                handleUploadRequest(message, dos, dis, fileArg);
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

    private static void handleDownload(String serverMessage, DataInputStream dis, String command) throws IOException {
        String[] parts = serverMessage.split(" ");
        long fileSize = Long.parseLong(parts[1]);
        String[] commandParts = command.split(" ");
        String fileName = commandParts[1];

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            byte[] buffer = new byte[Server.MAX_CHUNK_SIZE];
            long bytesReceived = 0;
            while (bytesReceived < fileSize) {
                int bytesRead = dis.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                fos.write(buffer, 0, bytesRead);
                bytesReceived += bytesRead;
            }
            System.out.println("File downloaded successfully: " + fileName);
        }
    }

    private static List<String> parseCommand(String command) {
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
}
