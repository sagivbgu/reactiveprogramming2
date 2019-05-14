package com.mlss.whatsapp_server;

import java.io.IOException;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;

public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_server", ConfigFactory.load());

        try {
            ActorSelection selection =
                    system.actorSelection("akka://whatsapp_manager@192.168.1.16:2552/user/manager");

            selection.tell(new ConnectRequest("ml"), Actor.noSender());
            System.in.read();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
