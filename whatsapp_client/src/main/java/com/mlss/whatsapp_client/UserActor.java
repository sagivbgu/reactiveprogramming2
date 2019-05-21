package com.mlss.whatsapp_client;

import akka.actor.*;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;


public class UserActor extends AbstractActor {
    static public Props props(ActorSelection managingServer) {
        return Props.create(UserActor.class, () -> new UserActor(managingServer));
    }

    private ActorSelection managingServer;
    private final AbstractActor.Receive connectedState;
    private final AbstractActor.Receive connectingState;
    private final AbstractActor.Receive disconnectedState;

    public UserActor(ActorSelection managingServer) {
        this.managingServer = managingServer;

        this.connectedState = receiveBuilder()
                .match(DisconnectRequest.class, this::OnDisconnectRequset)
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

    private void OnConnectionAccepted(ConnectionAccepted connection_accepted) {
        getContext().become(this.connectedState);
        System.out.println(
                String.format("%s has connected successfully!", connection_accepted.acceptedUsername)
        );
    }

    private void OnConnectionDenied(ConnectionDenied connection_denied) {
        getContext().become(this.disconnectedState);
        System.out.println(
                String.format("%s is     in use!", connection_denied.deniedUsername)
        );
    }

    private void OnDisconnectRequset(DisconnectRequest request) {
        getContext().become(this.disconnectedState);
        this.managingServer.tell(new DisconnectRequest(), getSelf());
    }
}
