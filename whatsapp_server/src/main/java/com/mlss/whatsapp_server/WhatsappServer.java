package com.mlss.whatsapp_server;

import java.io.IOException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class WhatsappServer {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("helloakka");
    try {
      
      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } catch (IOException ioe) {
    } finally {
      system.terminate();
    }
  }
}
