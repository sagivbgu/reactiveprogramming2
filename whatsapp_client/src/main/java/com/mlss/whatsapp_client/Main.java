package com.mlss.whatsapp_client;

import akka.actor.*;
import com.typesafe.config.ConfigFactory;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.Scanner;

/* Issues:

 * Serializing
 * Ports don't change with netty.tcp

 */


public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_client", ConfigFactory.load());

        try {
            ActorSelection managingServer = system.actorSelection("akka://whatsapp_manager@127.0.0.1:2552/user/manager");
            final ActorRef userActor = system.actorOf(UserActor.props(managingServer), "userActor");
            System.out.println("*** Welcome to Whatsapp ***");
            new CommandsExecutor(userActor).start();
        } finally {
            system.terminate();
        }
    }
}
