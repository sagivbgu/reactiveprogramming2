package com.mlss.whatsapp_client;

import akka.actor.*;
import com.typesafe.config.ConfigFactory;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;

import java.io.IOException;

/* Issues:

    * Serializing
    * Ports don't change with netty.tcp

*/


public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_client", ConfigFactory.load());

        try {
            ActorSelection managingServer = system.actorSelection("akka://whatsapp_manager@127.0.0.1:2552/user/manager");
            final ActorRef userActor = system.actorOf(Props.create(UserActor.class), "user_actor");

            userActor.tell(new ConnectRequest("asdf"), Actor.noSender());

            System.in.read();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
