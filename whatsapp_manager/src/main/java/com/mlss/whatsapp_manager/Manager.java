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
                .match(CreateGroupRequest.class, this::onCreateGroup)
                .match(LeaveGroupRequest.class, this::onLeaveGroup)
                .match(GroupSendMessage.class, this::onGroupSendMessage)
                .match(Terminated.class, this::onActorTermination)
                .build();
    }

    private void onConnectRequest(ConnectRequest request) {
        Object reply;
        if (usersToAddresses.containsKey(request.username)) {
            reply = new ConnectionDenied(request.username);
        } else {
            reply = new ConnectionAccepted(request.username);

            usersToAddresses.put(request.username, getSender().path().toString());
            getContext().watch(getSender());

            System.out.println("New user: " + request.username);
        }

        getSender().tell(reply, getSelf());
    }

    private void onDisconnectRequest(DisconnectRequest request) {
        String userPath = getSender().path().toString();
        System.out.println("User disconnected: " + getUsernameByPath(userPath));
        String username = getUsernameByPath(userPath);
        if (username == null) {
            System.out.println("Got disconnect request from disconnected user");
            return;
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
            if (entry.getValue().equals(groupActor)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void onCreateGroup(CreateGroupRequest createGroupRequest) {
        if (this.groupNamesToActors.containsKey(createGroupRequest.groupName)) {
            getSender().tell(
                    new CommandFailure(String.format("%s already exists!", createGroupRequest.groupName)),
                    getSelf()
            );
            return;
        }

        String groupCreatorUsername = getUsernameByPath(getSender().path().toString());

        ActorRef groupActor = getContext().actorOf(
                GroupActor.props(createGroupRequest.groupName, getSender(), groupCreatorUsername),
                String.format("Group__%s", createGroupRequest.groupName));

        getContext().watch(groupActor);
        groupNamesToActors.put(createGroupRequest.groupName, groupActor);

        // TODO: Why is it called "CommandFailure"? It's not a failure
        getSender().tell(
                new CommandFailure(String.format("%s created successfully!", createGroupRequest.groupName)),
                getSelf()
        );
        System.out.println(String.format("Group %s created", createGroupRequest.groupName));
    }

    private void onLeaveGroup(LeaveGroupRequest leaveGroupRequest) {
        // TODO: What if the creator leaves?
        groupNamesToActors.get(leaveGroupRequest.groupName).forward(leaveGroupRequest, getContext());
    }

    private void onGroupSendMessage(GroupSendMessage groupSendMessage) {
        if (!groupNamesToActors.containsKey(groupSendMessage.groupName)) {
            getSender().tell(
                    new CommandFailure(String.format("Group %s does not exist!", groupSendMessage.groupName)), getSelf()
            );
        } else {
            groupNamesToActors.get(groupSendMessage.groupName).forward(groupSendMessage.message, getContext());
        }
    }

    private void onActorTermination(Terminated t) {
        ActorRef terminatedActor = t.getActor();

        String terminatedGroupName = getGroupNameByActor(terminatedActor);
        if (terminatedGroupName != null && this.groupNamesToActors.containsKey(terminatedGroupName)) {
            this.groupNamesToActors.remove(terminatedGroupName);
            System.out.println("onActorTermination: Group %s terminated");
            return;
        }

        String terminatedUsername = getUsernameByPath(terminatedActor.path().toString());
        if (terminatedUsername != null && this.usersToAddresses.containsKey(terminatedUsername)) {
            this.usersToAddresses.remove(terminatedUsername);
            System.out.println("onActorTermination: User %s terminated");
            return;
        }

        // TODO: Delete this
        System.out.println("onActorTermination: Something wrong happened");
    }
}
