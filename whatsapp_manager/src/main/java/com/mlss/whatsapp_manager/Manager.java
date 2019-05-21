package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.Props;

import com.mlss.whatsapp_common.ManagerCommands.UserNotFound;
import com.mlss.whatsapp_common.ManagerCommands.UserAddressRequest;
import com.mlss.whatsapp_common.ManagerCommands.UserAddressResponse;
import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;

import java.util.HashMap;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, () -> new Manager());
    }

    HashMap<String, String> usersToAddresses;

    public Manager() {
        usersToAddresses = new HashMap<>();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, request -> {
                    System.out.println("New connection: " + request.username);

                    Object reply;
                    if (usersToAddresses.containsKey(request.username))
                    {
                        reply = new ConnectionDenied(request.username);
                    }
                    else
                    {
                        reply = new ConnectionAccepted(request.username);
                    }

                    getSender().tell(reply, getSelf());
                    usersToAddresses.put(request.username, getSender().path().address().toString());
                })
                .match(UserAddressRequest.class, request -> {
                    if (!usersToAddresses.containsKey(request.username)) {
                        getSender().tell(new UserNotFound(request.username), getSelf());
                    }
                    getSender().tell(new UserAddressResponse(usersToAddresses.get(request.username)), getSelf());
                })
                .build();
    }
}
