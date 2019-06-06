package com.mlss.whatsapp_client;

import akka.actor.*;

import com.mlss.whatsapp_common.GroupMessages.*;
import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.UserFeatures.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import akka.util.Timeout;

import com.typesafe.config.ConfigFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.util.concurrent.TimeUnit;


public class UserActor extends AbstractActor {
    static public Props props() {
        return Props.create(UserActor.class, () -> new UserActor());
    }

    private String username;
    private ActorSelection managingServer;
    private final Receive disconnectedState;
    private final Receive connectingState;
    private final Receive connectedState;
    private final Receive invitedState;
    private HashMap<String, ActorSelection> usersToActors;
    private HashMap<String, Queue<Message>> usersToMessageQueues;
    private Queue<ActorRef> groupInvites;

    public UserActor() {
        String managingServerAddress = ConfigFactory.load().getString("akka.remote.managing-server");
        this.managingServer = getContext().actorSelection(managingServerAddress);
        this.usersToActors = new HashMap<>();
        this.usersToMessageQueues = new HashMap<>();
        this.groupInvites = new LinkedList<>();

        this.disconnectedState = receiveBuilder()
                .match(ConnectRequest.class, this::onConnectRequest)
                .match(DisconnectAccepted.class, response -> System.out.println(String.format("%s has been disconnected successfully!", response.disconnectedUsername)))
                .matchAny(o -> System.out.println("Please connect before entering commands!"))
                .build();

        this.connectingState = receiveBuilder()
                .match(ConnectionAccepted.class, this::onConnectionAccepted)
                .match(ConnectionDenied.class, this::onConnectionDenied)
                .matchAny(o -> System.out.println("Please wait until connection succeeds before entering commands!"))
                .build();

        this.connectedState = receiveBuilder()
                .match(ConnectRequest.class, request -> System.out.println(String.format("Already connected as %s.", username)))
                .match(DisconnectRequest.class, this::onDisconnectRequest)
                .match(UserAddressResponse.class, this::onUserAddressResponse)
                .match(UserNotFound.class, this::onUserNotFound)
                .match(SendMessageRequest.class, this::onSendMessageRequest)
                .match(TextMessage.class, msg -> MessagePrinter.print(msg.message, msg.sender))
                .match(BinaryMessage.class, msg -> onBinaryMessage(msg, MessagePrinter.USER_SENDER))
                .match(CreateGroupRequest.class, createGroupRequest -> this.managingServer.tell(createGroupRequest, getSelf()))
                .match(LeaveGroupRequest.class, this::onLeaveGroupRequest)
                .match(GroupSendMessage.class, this::onGroupSendMessage)
                .match(GroupTextMessage.class, msg -> MessagePrinter.print(msg.message, msg.senderUsername, msg.groupName))
                .match(GroupBinaryMessage.class, groupMessage -> onBinaryMessage(groupMessage.message, groupMessage.groupName))
                .match(GroupInviteUserCommand.class, inviteCommand -> this.managingServer.tell(inviteCommand, getSelf()))
                .match(GroupRemoveUserCommand.class, removeUserCommand -> this.managingServer.tell(removeUserCommand, getSelf()))
                .match(GroupInviteMessage.class, this::onGroupInviteMessage)
                .match(MuteUserCommand.class, command -> this.managingServer.tell(command, getSelf()))
                .match(CommandFailure.class, failure -> System.out.println(failure.failureMessage))
                .match(GroupInviteResponse.class, o -> System.out.println("Illegal command"))
                .build();

        this.invitedState = receiveBuilder()
                .match(GroupInviteMessage.class, this::onGroupInviteMessage)
                .match(GroupInviteResponse.class, this::onGroupInviteResponse)
                .match(CommandFailure.class, failure -> System.out.println(failure.failureMessage))
                .matchAny(o -> System.out.println("Illegal command."))
                .build();
    }

    @Override
    public Receive createReceive() {
        return this.disconnectedState;
    }

    private void onConnectRequest(ConnectRequest request) {
        if (!validateActorOnline(managingServer)) {
            return;
        }

        getContext().become(this.connectingState);
        managingServer.tell(request, getSelf());
        // TODO: getContext().watch(managingServer)
    }

    // TODO: Move to common utils
    private boolean validateActorOnline(ActorSelection actor) {
        Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
        Future<ActorRef> rt = actor.resolveOne(timeout);

        try {
            Await.result(rt, timeout.duration());
            return true;
        } catch (Exception e) {
            System.out.println("server is offline!");
            return false;
        }
    }

    private void onConnectionAccepted(ConnectionAccepted response) {
        this.username = response.acceptedUsername;
        getContext().become(this.connectedState);
        System.out.println(String.format("%s has connected successfully!", response.acceptedUsername));
    }

    private void onConnectionDenied(ConnectionDenied response) {
        System.out.println(String.format("%s is in use!", response.deniedUsername));
        getContext().become(this.disconnectedState);
    }

    private void onDisconnectRequest(DisconnectRequest request) {
        getContext().become(this.disconnectedState);
        this.username = null;
        if (validateActorOnline(this.managingServer)) {
            this.managingServer.tell(request, getSelf());
        }
    }

    private void onLeaveGroupRequest(LeaveGroupRequest leaveGroupRequest) {
        leaveGroupRequest.leavingUsername = this.username;
        this.managingServer.tell(leaveGroupRequest, getSelf());
    }

    private void onUserAddressResponse(UserAddressResponse response) {
        ActorSelection userActor = getContext().actorSelection(response.address);
        this.usersToActors.put(response.username, userActor);
        this.usersToMessageQueues.get(response.username).forEach(msg ->
                userActor.tell(msg, getSelf())
        );

        this.usersToMessageQueues.replace(response.username, new LinkedList<>());
    }

    private void onUserNotFound(UserNotFound response) {
        System.out.println(String.format("%s does not exist!", response.username));
        this.usersToMessageQueues.remove(response.username);
    }

    private void onSendMessageRequest(SendMessageRequest request) {
        request.message.sender = this.username;
        ActorSelection targetActor = this.usersToActors.get(request.target);
        if (targetActor == null) {
            getUserMessagesQueue(request.target).add(request.message);
            this.managingServer.tell(new UserAddressRequest(request.target), getSelf());
        } else {
            targetActor.tell(request.message, getSelf());  // TODO: What if it fails? What if the user has disconnected?
        }
    }

    private void onGroupSendMessage(GroupSendMessage groupSendMessage) {
        groupSendMessage.message.sender = username;
        this.managingServer.tell(groupSendMessage, getSelf());
    }

    private Queue<Message> getUserMessagesQueue(String username) {
        if (!this.usersToMessageQueues.containsKey(username)) {
            this.usersToMessageQueues.put(username, new LinkedList<>());
        }
        return this.usersToMessageQueues.get(username);
    }

    private void onBinaryMessage(BinaryMessage message, String groupName) {
        String filePath = System.getProperty("user.dir") + File.separator + new Date().getTime() + "-" + message.fileName;
        try {
            Files.write(Paths.get(filePath), message.message);
            MessagePrinter.print(String.format("File received: %s", filePath), message.sender, groupName);
        } catch (IOException e) {
            System.out.println(String.format("Error writing new file from %s to %s", message.sender, filePath));
        }
    }

    private void onGroupInviteMessage(GroupInviteMessage inviteMessage) {
        MessagePrinter.print(
                String.format("You have been invited to %s, Accept?", inviteMessage.groupName),
                inviteMessage.inviterUsername,
                inviteMessage.groupName
        );

        this.groupInvites.add(getSender());
        getContext().become(this.invitedState);
    }

    private void onGroupInviteResponse(GroupInviteResponse inviteResponse) {
        if (this.groupInvites.size() == 0) {
            System.out.println("Illegal command.");
            return;
        }

        ActorRef groupActor = this.groupInvites.remove();
        groupActor.tell(inviteResponse, getSelf());

        if (this.groupInvites.size() == 0) {
            getContext().become(this.connectedState);
        }
    }
}
