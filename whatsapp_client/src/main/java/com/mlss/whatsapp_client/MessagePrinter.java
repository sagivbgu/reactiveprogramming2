package com.mlss.whatsapp_client;

import java.time.LocalDateTime;

public class MessagePrinter {
    public static void print(String message, String sender) {
        print(message, sender, "user");
    }

    public static void print(String message, String sender, String group) {
        String time = LocalDateTime.now().toString();
        System.out.println(String.format("[%s][%s][%s] %s", time, group, sender, message));
    }
}
