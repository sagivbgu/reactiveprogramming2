package com.mlss.whatsapp_client;

import akka.actor.*;

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

    public UserActor() {
        this.managingServer = null;

        this.connectedState = receiveBuilder()
                .match(DisconnectRequest.class, this::OnDisconnectRequest)
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
}
