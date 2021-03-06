package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;

import com.mlss.whatsapp_common.GroupMessages.*;
import com.mlss.whatsapp_common.ManagerMessages.*;

import java.util.HashMap;
import java.util.Map;


public class Manager extends AbstractActor {
    static public Props props() {
        return Props.create(Manager.class, Manager::new);
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
                .match(GroupInviteUserCommand.class, this::onGroupInviteUserCommand)
                .match(GroupRemoveUserCommand.class, this::onGroupRemoveUserCommand)
                .match(GroupSendMessage.class, msg -> forwardIfGroupExists(msg.message, msg.groupName))
                .match(MuteUserCommand.class, command -> forwardIfGroupExists(command, command.groupName))
                .match(UnmuteUserCommand.class, command -> forwardIfGroupExists(command, command.groupName))
                .match(CoadminAddRequest.class, command -> forwardIfGroupExists(command, command.groupName))
                .match(CoadminRemoveRequest.class, command -> forwardIfGroupExists(command, command.groupName))
                .match(LeaveGroupRequest.class, msg -> forwardIfGroupExists(msg, msg.groupName))
                .match(Terminated.class, this::onActorTermination)
                .build();
    }

    private void onGroupInviteUserCommand(GroupInviteUserCommand inviteUserCommand) {
        ActorRef groupActor = validateGroupExists(inviteUserCommand.groupName);
        if (groupActor == null) {
            return;
        }

        if (this.usersToAddresses.containsKey(inviteUserCommand.invitedUsername)) {
            inviteUserCommand.invitedUserAddress = this.usersToAddresses.get(inviteUserCommand.invitedUsername);
        }

        groupActor.forward(inviteUserCommand, getContext());
    }

    private void onGroupRemoveUserCommand(GroupRemoveUserCommand removeUserCommand) {
        ActorRef groupActor = validateGroupExists(removeUserCommand.groupName);
        if (groupActor == null) {
            return;
        }

        if (this.usersToAddresses.containsKey(removeUserCommand.userToRemove)) {
            removeUserCommand.removedUserAddress = this.usersToAddresses.get(removeUserCommand.userToRemove);
        }

        groupActor.forward(removeUserCommand, getContext());
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
        String username = getUsernameByPath(userPath);
        if (username == null) {
            System.out.println("Got disconnect request from disconnected user");
            return;
        }

        System.out.println("User disconnected: " + getUsernameByPath(userPath));
        this.usersToAddresses.remove(username);

        this.groupNamesToActors.forEach((groupName, groupActor) ->
            groupActor.forward(request, getContext())
        );

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
                    new GeneralMessage(String.format("%s already exists!", createGroupRequest.groupName)),
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

        getSender().tell(
                new GeneralMessage(String.format("%s created successfully!", createGroupRequest.groupName)),
                getSelf()
        );
        System.out.println(String.format("Group %s created", createGroupRequest.groupName));
    }

    private void forwardIfGroupExists(Object message, String groupName) {
        ActorRef groupActor = validateGroupExists(groupName);
        if (groupActor != null) {
            groupActor.forward(message, getContext());
        }
    }

    private void onActorTermination(Terminated t) {
        ActorRef terminatedActor = t.getActor();

        String terminatedGroupName = getGroupNameByActor(terminatedActor);
        if (terminatedGroupName != null && this.groupNamesToActors.containsKey(terminatedGroupName)) {
            this.groupNamesToActors.remove(terminatedGroupName);
            System.out.println(String.format("onActorTermination: Group %s terminated", terminatedGroupName));
            return;
        }

        String terminatedUsername = getUsernameByPath(terminatedActor.path().toString());
        if (terminatedUsername != null && this.usersToAddresses.containsKey(terminatedUsername)) {
            this.usersToAddresses.remove(terminatedUsername);
            System.out.println(String.format("onActorTermination: User %s terminated", terminatedUsername));
        }
    }

    private ActorRef validateGroupExists(String groupName) {
        ActorRef groupActor = this.groupNamesToActors.get(groupName);

        if (groupActor == null) {
            getSender().tell(new GeneralMessage(String.format("%s does not exist!", groupName)), getSelf());
        }

        return groupActor;
    }
}
