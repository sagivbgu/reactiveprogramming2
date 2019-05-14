package com.mlss.whatsapp_server;

import java.io.IOException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("whatsapp_server");
    try {
      
      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } catch (IOException ioe) {
    } finally {
      system.terminate();
    }
  }
}
