package com.mlss.whatsapp_client;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MessagePrinter {
    public static final String USER_SENDER = "user";

    public static void print(String message, String sender) {
        print(message, sender, "user");
    }

    public static void print(String message, String sender, String group) {
        String time = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
        System.out.println(String.format("[%s][%s][%s] %s", time, group, sender, message));
    }

    public static void printRaw(String message) {
        System.out.println(message);
    }
}
