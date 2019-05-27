package com.mlss.whatsapp_client;

import akka.actor.*;

import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.UserFeatures.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectAccepted;

import scala.concurrent.Await;
import scala.concurrent.Future;

import java.util.concurrent.TimeUnit;


public class UserActor extends AbstractActor {
    static public Props props() {
        return Props.create(UserActor.class, () -> new UserActor());
    }

    private ActorSelection managingServer;
    private final AbstractActor.Receive connectedState;
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

    public UserActor() {
        this.managingServer = null;
        this.usersToActors = new HashMap<>();
        this.usersToMessageQueues = new HashMap<>();

        this.connectedState = receiveBuilder()
                .match(DisconnectRequest.class, this::OnDisconnectRequest)
                .match(UserAddressResponse.class, this::onUserAddressResponse)
                .match(UserNotFound.class, this::onUserNotFound)
                .match(SendMessageRequest.class, this::onSendMessageRequest)
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

    private Object sendBlockingRequest(ActorSelection managingServer, Object request) {
        Timeout timeout = new Timeout(2, TimeUnit.SECONDS);
        Object result = null;

        Future<Object> rt = Patterns.ask(managingServer, request, timeout.duration().toMillis());
        try {
            result = Await.result(rt, timeout.duration());
        } catch (Exception e) {
            System.out.println("server is offline!");
        }

        return result;
    }

    private void OnConnectRequest(ConnectRequest request) {
        ActorSelection managingServer = getContext().actorSelection("akka://whatsapp_manager@127.0.0.1:2552/user/manager");

        Object result = sendBlockingRequest(managingServer, request);
        if (result == null) {
            return;
        }

        String print_message;
        if (result instanceof ConnectionAccepted) {
            print_message = String.format("%s has connected successfully!", ((ConnectionAccepted) result).acceptedUsername);
            this.managingServer = managingServer;
            getContext().become(this.connectedState);
        } else {
            print_message = String.format("%s is in use!", ((ConnectionDenied) result).deniedUsername);
        }

        System.out.println(print_message);
    }

    private void OnDisconnectRequest(DisconnectRequest request) {
        Object result = sendBlockingRequest(this.managingServer, request);
        getContext().become(this.disconnectedState);

        if (result == null) {
            return;
        }

        this.managingServer = null;

        if (result instanceof DisconnectAccepted) {
            System.out.println(
                    String.format("%s has been disconnected successfully!", ((DisconnectAccepted) result).disconnectedUsername)
            );
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
