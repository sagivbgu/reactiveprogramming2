package com.mlss.whatsapp_client;

import akka.actor.*;
import com.typesafe.config.ConfigFactory;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/* Issues:

 * Serializing
 * Ports don't change with netty.tcp

 */


public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_client", ConfigFactory.load());

        try {
            final ActorRef userActor = system.actorOf(UserActor.props(), "user_actor");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String ba = null;

            System.out.println("Press to connect: ");
            ba = reader.readLine();

            userActor.tell(new ConnectRequest("kaki"), ActorRef.noSender());

            System.out.println("Press to disconnect: ");
            ba = reader.readLine();

            userActor.tell(new DisconnectRequest(), ActorRef.noSender());

            System.out.println("Press to exit: ");
            ba = reader.readLine();

            //CommandsExecuter.start(userActor);
        } catch (IOException e) {
        } finally {
            system.terminate();
        }
    }
}
