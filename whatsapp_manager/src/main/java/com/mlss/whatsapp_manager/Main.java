package com.mlss.whatsapp_manager;

import akka.actor.Props;
import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;

public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_manager", ConfigFactory.load());

        try {
            system.actorOf(Props.create(Manager.class), "manager");

            System.out.println("Running...");
            System.in.read();
        } catch (java.io.IOException exp) {
        } finally {
            system.terminate();
        }
    }
}
