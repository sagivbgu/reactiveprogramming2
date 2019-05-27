package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;

import com.mlss.whatsapp_common.ManagerCommands.UserNotFound;
import com.mlss.whatsapp_common.ManagerCommands.UserAddressRequest;
import com.mlss.whatsapp_common.ManagerCommands.UserAddressResponse;
import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;

import java.util.HashMap;
import java.util.Map;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, () -> new Manager());
    }

    private HashMap<String, String> usersToAddresses;

    public Manager() {
        usersToAddresses = new HashMap<>();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, this::onConnectRequest)
                .match(UserAddressRequest.class, this::onUserAddressRequest)
                .match(Terminated.class, this::onUserTermination)
                .build();
    }

    private void onConnectRequest(ConnectRequest request) {
        System.out.println("New connection: " + request.username);

        Object reply;
        if (usersToAddresses.containsKey(request.username)) {
            reply = new ConnectionDenied(request.username);
        } else {
            reply = new ConnectionAccepted(request.username);
            getContext().watch(getSender());
        }

        getSender().tell(reply, getSelf());
        usersToAddresses.put(request.username, getSender().path().toString());
    }

    private void onUserAddressRequest(UserAddressRequest request) {
        System.out.println("Got user address request: " + request.username);
        Object reply;
        if (!usersToAddresses.containsKey(request.username)) {
            reply = new UserNotFound(request.username);
        } else {
            reply = new UserAddressResponse(request.username, usersToAddresses.get(request.username));
        }
        getSender().tell(reply, getSelf());
    }

    private void onUserTermination(Terminated t) {
        ActorRef terminatedActor = t.actor();
        String actorPath = terminatedActor.path().toString();

        int prevSize = usersToAddresses.size();

        for (Map.Entry<String, String> entry : usersToAddresses.entrySet()) {
            if (entry.getValue().equals(actorPath)) {
                usersToAddresses.remove(entry.getKey());
                break;
            }
        }

        if (prevSize == usersToAddresses.size()) {
            throw new IllegalStateException();
        }
    }
}
