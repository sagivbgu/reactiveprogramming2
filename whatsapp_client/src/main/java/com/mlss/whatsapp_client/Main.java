package com.mlss.whatsapp_client;

import akka.actor.*;
import com.typesafe.config.ConfigFactory;

public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_client", ConfigFactory.load());

        try {
            final ActorRef userActor = system.actorOf(UserActor.props(), "userActor");
            System.out.println("*** Welcome to Whatsapp ***");
            new CommandsExecutor(userActor).start();
        } finally {
            system.terminate();
        }
    }
}
