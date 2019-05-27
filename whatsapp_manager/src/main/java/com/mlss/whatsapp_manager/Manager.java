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
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectAccepted;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

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
                .match(DisconnectRequest.class, this::onDisconnectRequest)
                .match(UserAddressRequest.class, this::onUserAddressRequest)
                .match(Terminated.class, this::onUserTermination)
                .build();
    }

    private void onConnectRequest(ConnectRequest request) {
        Object reply;
        if (usersToAddresses.containsKey(request.username)) {
            reply = new ConnectionDenied(request.username);
        } else {
            reply = new ConnectionAccepted(request.username);

            usersToAddresses.put(request.username, getSender().path().address().toString());
            getContext().watch(getSender());

            System.out.println("New user: " + request.username + " " + getSender().path().address().toString());
        }

        getSender().tell(reply, getSelf());
    }

    private void onDisconnectRequest(DisconnectRequest request) {
        String userPath = getSender().path().address().toString();
        System.out.println("User disconnected: " + userPath);
        String username = getUsernameByPath(userPath);
        if (username == null) {
            throw new IllegalArgumentException();
        }

        this.usersToAddresses.remove(username);

        getSender().tell(new DisconnectAccepted(username), getSelf());
        getContext().unwatch(getSender());
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
        System.out.println("addressTerminated: " + t.getAddressTerminated());
        System.out.println("existenceConfirmed: " + t.getExistenceConfirmed());

        ActorRef terminatedActor = t.getActor();
        if (!terminatedActor.isTerminated()) {
            return;
        }

        String userPath = terminatedActor.path().address().toString();
        String username = getUsernameByPath(userPath);
        if (username == null) {
            throw new IllegalStateException();
        }

        usersToAddresses.remove(username);
        System.out.println("Terminated: " + username);
    }

    private String getUsernameByPath(String userPath) {
        for (Map.Entry<String, String> entry : usersToAddresses.entrySet()) {
            if (entry.getValue().equals(userPath)) {
                return entry.getKey();
            }
        }

        return null;
    }
}
