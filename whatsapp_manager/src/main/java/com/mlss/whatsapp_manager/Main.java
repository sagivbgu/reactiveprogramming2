package com.mlss.whatsapp_manager;

import java.io.IOException;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.routing.BroadcastGroup;
import akka.routing.Router;
import com.typesafe.config.ConfigFactory;

import com.mlss.whatsapp_manager.Manager;
import com.mlss.whatsapp_manager.Greeter.WhoToGreet;
import com.mlss.whatsapp_common.UserFeatures;

public class Main {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp_manager", ConfigFactory.load());

        try {
            system.actorOf(Props.create(Manager.class), "manager");


            System.in.read();
        } catch (java.io.IOException exp) {
        } finally {
            system.terminate();
        }
    }
}
