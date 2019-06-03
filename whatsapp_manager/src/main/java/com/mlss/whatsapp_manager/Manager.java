package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;

import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.UserFeatures.*;

import java.util.HashMap;
import java.util.Map;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, () -> new Manager());
    }

    private HashMap<String, String> usersToAddresses;
    private HashMap<String, ActorRef> groupNamesToActors;

    public Manager() {
        usersToAddresses = new HashMap<>();
        groupNamesToActors = new HashMap<>();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, this::onConnectRequest)
                .match(DisconnectRequest.class, this::onDisconnectRequest)
                .match(UserAddressRequest.class, this::onUserAddressRequest)
                .match(CreateGroup.class, this::onCreateGroup)
                .match(GroupSendText.class, this::onGroupSendText)
                .match(Terminated.class, this::onActorTermination)
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

    private void onActorTermination(Terminated t) {
        ActorRef terminatedActor = t.getActor();
        System.out.println("Group terminated");

        if (this.groupNamesToActors.containsValue(terminatedActor)) {
            this.groupNamesToActors.remove(getGroupNameByActor(terminatedActor));
            return;
        }

        if (this.usersToAddresses.containsValue(terminatedActor)) {
            this.usersToAddresses.remove(getUsernameByPath(terminatedActor.toString()));
            return;
        }

        System.out.println("onActorTermination: Something wrong happened");
    }

    private String getUsernameByPath(String userPath) {
        for (Map.Entry<String, String> entry : usersToAddresses.entrySet()) {
            if (entry.getValue().equals(userPath)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getGroupNameByActor(ActorRef groupActor) {
        for (Map.Entry<String, ActorRef> entry : this.groupNamesToActors.entrySet()) {
            if (entry.getValue() == groupActor) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void onCreateGroup(CreateGroup createGroup) {
        if (this.groupNamesToActors.containsKey(createGroup.groupName)) {
            getSender().tell(
                    new CommandFailure(String.format("%s already exists!", createGroup.groupName)),
                    getSelf()
            );
            return;
        }

        String groupCreatorUsername = getUsernameByPath(getSender().path().toString());

        ActorRef groupActor = getContext().actorOf(
                GroupActor.props(createGroup.groupName, getSender(), groupCreatorUsername),
                String.format("Group__%s", createGroup.groupName));

        getContext().watch(groupActor);
        groupNamesToActors.put(createGroup.groupName, groupActor);

        System.out.println(String.format("Group %s created", createGroup.groupName));
    }

    private void onGroupSendText(GroupSendText groupSendText) {
        if (!groupNamesToActors.containsKey(groupSendText.groupName)) {
            getSender().tell(
                    new CommandFailure(String.format("%s does not exist!", groupSendText.groupName)), getSelf()
            );
            return;
        }

        groupNamesToActors.get(groupSendText.groupName).forward(groupSendText, getContext());
    }
}
