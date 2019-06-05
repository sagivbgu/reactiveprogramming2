package com.mlss.whatsapp_manager;

import java.util.HashMap;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Router;

import com.mlss.whatsapp_common.GroupMessages.*;
import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.UserFeatures;
import com.mlss.whatsapp_common.UserFeatures.*;


public class GroupActor extends AbstractActor {
    static public Props props(String groupName, ActorRef groupCreatorActor, String groupCreateUsername) {
        return Props.create(GroupActor.class, () -> new GroupActor(groupName, groupCreatorActor, groupCreateUsername));
    }

    private enum Previledges {
        MUTED_USER,
        USER,
        CO_ADMIN,
        ADMIN
    }

    private class UserInfo {
        public String username;
        public Previledges previledges;

        public UserInfo(String username, Previledges previledges) {
            this.username = username;
            this.previledges = previledges;
        }
    }

    private final String groupName;
    private final ActorRef groupCreatorActor;
    private final AbstractActor.Receive receiverBuilder;
    private Router router;
    private final HashMap<ActorRef, UserInfo> actorToUserInfo;

    public GroupActor(String groupName, ActorRef groupCreatorActor, String groupCreatorUsername) {
        this.groupName = groupName;
        this.groupCreatorActor = groupCreatorActor;

        this.receiverBuilder = receiveBuilder()
                .match(LeaveGroupRequest.class, this::onLeaveGroupRequest)
                .match(TextMessage.class, this::OnGroupSendText)
                .match(BinaryMessage.class, this::OnGroupSendFile)
                .match(Terminated.class, this::onTerminated)
                .build();

        this.router = new Router(new BroadcastRoutingLogic());
        getContext().watch(groupCreatorActor);
        this.router = this.router.addRoutee(groupCreatorActor);

        this.actorToUserInfo = new HashMap<>();
        this.actorToUserInfo.put(groupCreatorActor, new UserInfo(groupCreatorUsername, Previledges.ADMIN));
    }

    @Override
    public Receive createReceive() {
        return this.receiverBuilder;
    }

    private void onLeaveGroupRequest(LeaveGroupRequest leaveGroupRequest) {
        if (leaveGroupRequest.groupName != this.groupName) {
            System.out.println("Manager did something wrong");
            return;
        }

        if (!this.actorToUserInfo.containsValue(getSender())) {
            String errorMessage = String.format("%s is not in %s!", leaveGroupRequest.leavingUsername, leaveGroupRequest.groupName);
            System.out.println(errorMessage);
            getSender().tell(new CommandFailure(errorMessage), getSelf());
            return;
        }

        UserInfo leavingUser = this.actorToUserInfo.get(getSender());
        switch (leavingUser.previledges) {
            case ADMIN:
//                this.router.route(new );
                getContext().stop(getSelf());
                break;
        }
    }

    private void OnGroupSendText(TextMessage message) {
        if (validateUserInGroup()) {
        // Validate Previledges
        router.route(new GroupTextMessage(this.groupName, message.sender, message.message), getSender());
        }
    }

    private void OnGroupSendFile(BinaryMessage message) {
        if (validateUserInGroup()) {
        // Validate Previledges
        router.route(new GroupBinaryMessage(this.groupName, message), getSender());
        }
    }

    private boolean validateUserInGroup() {
        if (!this.actorToUserInfo.containsKey(getSender())) {
            getSender().tell(new CommandFailure(String.format("You are not part of %s!", this.groupName)), getSelf());
            return false;
        }
        return true;
    }

    private void onTerminated(Terminated t) {
        this.router = this.router.removeRoutee(t.getActor());
    }
}