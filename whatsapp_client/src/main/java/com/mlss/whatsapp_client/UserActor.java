package com.mlss.whatsapp_client;

import akka.actor.*;

import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.UserFeatures.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;


public class UserActor extends AbstractActor {
    static public Props props(ActorSelection managingServer) {
        return Props.create(UserActor.class, () -> new UserActor(managingServer));
    }

    private ActorSelection managingServer;
    private final AbstractActor.Receive connectedState;
    private final AbstractActor.Receive connectingState;
    private final AbstractActor.Receive disconnectedState;
    private HashMap<String, ActorSelection> usersToActors;
    private HashMap<String, Queue<Object>> usersToMessageQueues;

    static public class TextMessage implements Serializable {
        public final String message;

        public TextMessage(String message) {
            this.message = message;
        }
    }

    static public class BinaryMessage implements Serializable {
        public final byte[] message;

        public BinaryMessage(byte[] message) {
            this.message = message;
        }
    }

    static public class SendMessageRequest implements Serializable {
        public final String target;
        public final Object message;

        public SendMessageRequest(String target, Object message) {
            this.target = target;
            this.message = message;
        }
    }

    public UserActor(ActorSelection managingServer) {
        this.managingServer = managingServer;
        this.usersToActors = new HashMap<>();
        this.usersToMessageQueues = new HashMap<>();

        this.connectedState = receiveBuilder()
                .match(DisconnectRequest.class, this::OnDisconnectRequest)
                .match(UserAddressResponse.class, this::onUserAddressResponse)
                .match(UserNotFound.class, this::onUserNotFound)
                .match(SendMessageRequest.class, this::onSendMessageRequest)
                .build();

        this.connectingState = receiveBuilder()
                .match(ConnectionAccepted.class, this::OnConnectionAccepted)
                .match(ConnectionDenied.class, this::OnConnectionDenied)
                .build();

        this.disconnectedState = receiveBuilder()
                .match(ConnectRequest.class, this::OnConnectRequest)
                .matchAny(o -> System.out.println("Please connect before entering commands!"))
                .build();
    }

    @Override
    public Receive createReceive() {
        return this.disconnectedState;
    }

    private void OnConnectRequest(ConnectRequest request) {
        getContext().become(this.connectingState);
        this.managingServer.tell(request, getSelf());
    }

    private void OnConnectionAccepted(ConnectionAccepted connectionAccepted) {
        getContext().become(this.connectedState);
        System.out.println(
                String.format("%s has connected successfully!", connectionAccepted.acceptedUsername)
        );
    }

    private void OnConnectionDenied(ConnectionDenied connectionDenied) {
        getContext().become(this.disconnectedState);
        System.out.println(
                String.format("%s is in use!", connectionDenied.deniedUsername)
        );
    }

    private void OnDisconnectRequest(DisconnectRequest request) {
        getContext().become(this.disconnectedState);
        this.managingServer.tell(new DisconnectRequest(), getSelf());
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
        ActorSelection targetActor = this.usersToActors.get(request.target);
        if (targetActor == null) {
            getUserMessagesQueue(request.target).add(request.message);
            this.managingServer.tell(new UserAddressRequest(request.target), getSelf());
        } else {
            targetActor.tell(request.message, getSelf());  // TODO: What if it fails? What if the user has disconnected?
        }
    }

    private Queue<Object> getUserMessagesQueue(String username) {
        if (!this.usersToMessageQueues.containsKey(username)) {
            this.usersToMessageQueues.put(username, new LinkedList<>());
        }
        return this.usersToMessageQueues.get(username);
    }
}
