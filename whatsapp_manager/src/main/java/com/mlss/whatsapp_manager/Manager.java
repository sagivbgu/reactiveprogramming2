package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;

import com.mlss.whatsapp_common.GroupMessages.*;
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
                .match(LeaveGroupRequest.class, msg -> forwardIfGroupExists(msg, msg.groupName))
                .match(GroupSendMessage.class, msg -> forwardIfGroupExists(msg, msg.groupName))
                .match(GroupInviteUserCommand.class, this::onGroupInviteUserCommand)
                .match(MuteUserCommand.class, msg -> forwardIfGroupExists(msg, msg.groupName))
                .match(Terminated.class, this::onActorTermination)
                .build();
    }

    // TODO: Check what happens if user got invite, but meanwhile the group was terminated

    private void onGroupInviteUserCommand(GroupInviteUserCommand inviteUserCommand) {
        if (validateGroupExists(inviteUserCommand.groupName)) {
            inviteUserCommand.invitedUserAddress = null;
            if (this.usersToAddresses.containsKey(inviteUserCommand.invitedUsername)) {
                inviteUserCommand.invitedUserAddress = this.usersToAddresses.get(inviteUserCommand.invitedUsername);
            }

            this.groupNamesToActors.get(inviteUserCommand.groupName).forward(inviteUserCommand, getContext());
        }
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

    private void forwardIfGroupExists(Object message, String groupName) {
        ActorRef groupActor = groupNamesToActors.get(groupName);
        if (groupActor == null) {
            getSender().tell(
                    new CommandFailure(String.format("%s does not exist!", groupName)), getSelf());
        } else {
            groupActor.forward(message, getContext());
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

    private boolean validateGroupExists(String groupName) {
        if (!this.groupNamesToActors.containsKey(groupName)) {
            getSender().tell(new CommandFailure(String.format("%s does not exist!", groupName)), getSelf());
            return false;
        }
        return true;
    }
}
