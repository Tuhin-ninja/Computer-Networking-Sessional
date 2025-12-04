package com.fileserver;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    public static synchronized void log(String username, String action, String fileName, String status) {
        try (PrintWriter out = new PrintWriter(new FileWriter("clients/" + username + "/log.txt", true))) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println(timestamp + " | " + action + " | " + fileName + " | " + status);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
