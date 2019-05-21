package com.mlss.whatsapp_client;

import java.io.IOException;
import java.util.Scanner;

import akka.actor.ActorRef;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;


public class CommandsExecuter {
    public static void start(ActorRef userActor) {

        Scanner sc = new Scanner(System.in);
        while (true) {
            String command = sc.nextLine();

            if (command.equals("exit")) {
                break;
            }

            // TODO: Find CLI parser
        }
    }
}
