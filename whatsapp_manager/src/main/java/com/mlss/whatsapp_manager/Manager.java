package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.Props;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;

import java.util.List;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, () -> new Manager());
    }

    public Manager() {
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, request -> {
                    System.out.println("New connection: " + request.username);
                    System.out.println(getSender().path());
                    System.out.println(getSender().path().address());
                    System.out.println(getSender().path().address().host());
                    System.out.println(getSender().path().address().port());
                })
                .build();
    }
}
