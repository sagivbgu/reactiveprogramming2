package com.mlss.whatsapp_manager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Router;

public class GroupActor extends AbstractActor {
    static public Props props(ActorRef groupCreator) {
        return Props.create(GroupActor.class, () -> new GroupActor(groupCreator));
    }

    ActorRef groupCreator;
    private Router router;

    public GroupActor(ActorRef groupCreator) {
        getContext().watch(groupCreator);
        router = new Router(new BroadcastRoutingLogic());
        router.addRoutee(groupCreator);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}

