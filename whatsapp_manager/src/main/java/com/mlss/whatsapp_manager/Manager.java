package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.Props;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, () -> new Manager());
    }

    HashMap<String, ActorPath> users;

    public Manager() {
        users = new HashMap<>();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, request -> {
                    System.out.println("New connection: " + request.username);

                    Object reply;
                    if (users.containsKey(request.username))
                    {
                        reply = new ConnectionDenied(request.username);
                    }
                    else
                    {
                        reply = new ConnectionAccepted(request.username);
                    }

                    getSender().tell(reply, getSelf());
                    users.put(request.username, getSender().path());
                })
                .build();
    }
}
