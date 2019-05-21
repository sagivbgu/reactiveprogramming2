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
                .build();

        this.disconnectedState = receiveBuilder()
                .match(ConnectRequest.class, this::OnConnectRequest)
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, this::OnConnectRequest)
                .matchAny(o -> System.out.println("Please connect before entering commands!"))
                .build();
    }

    private void OnConnectRequest(ConnectRequest request) {
        getContext().become(connectingState);
        managingServer.tell(request, getSelf());
    }

    private void OnConnectionAccepted(ConnectionAccepted connectionAccepted) {
        getContext().become(connectedState);
        System.out.println(
                String.format("%s has connected successfully", connectionAccepted.acceptedUsername)
        );
    }

    private void OnConnectionDenied(ConnectionDenied connectionDenied) {
        getContext().become(disconnectedState);
        System.out.println(
                String.format("{} is in use!", connectionDenied.deniedUsername)
        );
    }

    private void OnDisconnectRequset(DisconnectRequest request) {
        getContext().become(disconnectedState);
        managingServer.tell(new DisconnectRequest(), getSelf());
    }
}
