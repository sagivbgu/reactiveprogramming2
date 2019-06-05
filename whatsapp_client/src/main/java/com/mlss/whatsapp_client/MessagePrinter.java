package com.mlss.whatsapp_client;

import java.time.LocalDateTime;

// TODO: This should be actor?
public class MessagePrinter {
    public static void print(String message, String sender) {
        print(message, sender, "user");
    }

    public static void print(String message, String sender, String group) {
        String time = LocalDateTime.now().toString();
        System.out.println(String.format("[%s][%s][%s] %s", time, group, sender, message));
    }

    public static void printRaw(String message) {
        System.out.println(message);
    }
}
