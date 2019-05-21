package com.mlss.whatsapp_client;

import akka.actor.AbstractActor;
import akka.actor.ActorSelection;
import akka.actor.Props;

import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;
import com.mlss.whatsapp_common.UserFeatures.DisconnectRequest;
import com.mlss.whatsapp_common.UserFeatures.ConnectionAccepted;
import com.mlss.whatsapp_common.UserFeatures.ConnectionDenied;


public class UserActor extends AbstractActor {
    static public Props props() {
        return Props.create(UserActor.class, () -> new UserActor());
    }

    private ActorSelection managing_server;
    private final AbstractActor.Receive connected_state;
    private final AbstractActor.Receive connecting_state;
    private final AbstractActor.Receive disconnected_state;

    public UserActor() {
        managing_server = null;

        connected_state = receiveBuilder()
                .match(DisconnectRequest.class, this::OnDisonnectRequset)
                .build();

        connecting_state = receiveBuilder()
                .match(ConnectionAccepted.class, this::OnConnectionAccepted)
                .build();

        disconnected_state = receiveBuilder()
                .match(ConnectRequest.class, this::OnConnectRequset)
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectRequest.class, this::OnConnectRequset)
                .matchAny(o -> System.out.println("Please connect before entering commands!"))
                .build();
    }

    private void OnConnectRequset(ConnectRequest request) {
        managing_server = getContext().actorSelection("akka://whatsapp_manager@127.0.0.1:2552/user/manager");
        getContext().become(connecting_state);
        managing_server.tell(request, getSelf());
    }

    private void OnConnectionAccepted(ConnectionAccepted connection_accepted) {
        getContext().become(connected_state);
        System.out.println(
                String.format("%s has connected successfully", connection_accepted.accepted_username)
        );
    }

    private void OnConnectionDenied(ConnectionDenied connection_denied) {
        getContext().become(disconnected_state);
        System.out.println(
                String.format("{} is in use!", connection_denied.denied_username)
        );
    }

    private void OnDisonnectRequset(DisconnectRequest request) {
        getContext().become(disconnected_state);
        managing_server.tell(new DisconnectRequest(), getSelf());
    }
}
