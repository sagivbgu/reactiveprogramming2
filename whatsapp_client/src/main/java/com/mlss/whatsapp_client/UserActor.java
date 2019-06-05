package com.mlss.whatsapp_client;

import akka.actor.*;

import com.mlss.whatsapp_common.GroupMessages.*;
import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.UserFeatures.*;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import akka.util.Timeout;

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
    private HashMap<String, ActorSelection> usersToActors;
    private HashMap<String, Queue<Message>> usersToMessageQueues;

    static abstract public class Message implements Serializable {
        public String sender;

        public Message() {
        }

        public void setSender(String sender) {
            this.sender = sender;
        }
    }

    static public class TextMessage extends Message implements Serializable {
        public final String message;

        public TextMessage(String message) {
            super();
            this.message = message;
        }
    }

    static public class BinaryMessage extends Message implements Serializable {
        public final byte[] message;
        public final String fileName;

        public BinaryMessage(byte[] message, String fileName) {
            super();
            this.message = message;
            this.fileName = fileName;
        }
    }

    static public class SendMessageRequest implements Serializable {
        public final String target;
        public final Message message;

        public SendMessageRequest(String target, Message message) {
            this.target = target;
            this.message = message;
        }
    }

    public UserActor() {
        this.managingServer = getContext().actorSelection("akka://whatsapp_manager@127.0.0.1:2552/user/manager");
        this.usersToActors = new HashMap<>();
        this.usersToMessageQueues = new HashMap<>();

        this.disconnectedState = receiveBuilder()
                .match(ConnectRequest.class, this::onConnectRequest)
                .matchAny(o -> System.out.println("Please connect before entering commands!"))
                .build();

        this.connectingState = receiveBuilder()
                .match(ConnectionAccepted.class, this::onConnectionAccepted)
                .match(ConnectionDenied.class, this::onConnectionDenied)
                .matchAny(o -> System.out.println("Please wait until connection succeeds before entering commands!"))
                .build();

        this.connectedState = receiveBuilder()
                .match(DisconnectRequest.class, this::onDisconnectRequest)
                .match(DisconnectAccepted.class, response -> System.out.println(String.format("%s has been disconnected successfully!", response.disconnectedUsername)))
                .match(UserAddressResponse.class, this::onUserAddressResponse)
                .match(UserNotFound.class, this::onUserNotFound)
                .match(SendMessageRequest.class, this::onSendMessageRequest)
                .match(TextMessage.class, this::onTextMessage)
                .match(BinaryMessage.class, this::onBinaryMessage)
                .match(CreateGroup.class, createGroup -> this.managingServer.tell(createGroup, getSelf()))
                .match(GroupSendText.class, groupSendText -> this.managingServer.tell(groupSendText, getSelf()))
                .match(NewGroupText.class, newGroupText -> System.out.println(String.format("%s:%s:%s", newGroupText.groupName, newGroupText.senderUsername, newGroupText.message)))
                .match(CommandFailure.class, failure -> System.out.println(failure.failureMessage))
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
        request.message.setSender(this.username);
        ActorSelection targetActor = this.usersToActors.get(request.target);
        if (targetActor == null) {
            getUserMessagesQueue(request.target).add(request.message);
            this.managingServer.tell(new UserAddressRequest(request.target), getSelf());
        } else {
            targetActor.tell(request.message, getSelf());  // TODO: What if it fails? What if the user has disconnected?
        }
    }

    private Queue<Message> getUserMessagesQueue(String username) {
        if (!this.usersToMessageQueues.containsKey(username)) {
            this.usersToMessageQueues.put(username, new LinkedList<>());
        }
        return this.usersToMessageQueues.get(username);
    }

    private void onTextMessage(TextMessage message) {
        MessagePrinter.print(message.message, message.sender);
    }

    private void onBinaryMessage(BinaryMessage message) {
        String filePath = System.getProperty("user.dir") + File.separator + new Date().getTime() + "-" + message.fileName;
        try {
            Files.write(Paths.get(filePath), message.message);
            MessagePrinter.print(String.format("File received: %s", filePath), message.sender);
        } catch (IOException e) {
            System.out.println(String.format("Error writing new file from %s to %s", message.sender, filePath));
        }
    }
}
