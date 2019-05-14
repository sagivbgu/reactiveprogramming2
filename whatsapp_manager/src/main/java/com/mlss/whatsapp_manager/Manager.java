package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.Props;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;

import java.util.List;
import java.util.Map;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, () -> new Manager());
    }

    Map<String, ActorPath> users;

    public Manager() {
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, request -> {
                    System.out.println("New connection: " + request.username);

                    Object reply;
                    if (users.containsKey(request.username))
                    {
                        reply = new ConnectionDenied();
                    }
                    else
                    {
                        reply = new ConnectionAccepted();
                    }

                    getSender().tell(reply, getSelf());
                    users.put(request.username, getSender().path());
                })
                .build();
    }
}
