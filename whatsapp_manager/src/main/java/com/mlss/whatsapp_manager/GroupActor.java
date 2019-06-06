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
import com.mlss.whatsapp_common.UserFeatures.*;


public class GroupActor extends AbstractActor {
    static public Props props(String groupName, ActorRef groupCreatorActor, String groupCreateUsername) {
        return Props.create(GroupActor.class, () -> new GroupActor(groupName, groupCreatorActor, groupCreateUsername));
    }

    private enum Privileges {
        MUTED_USER(0), USER(1), CO_ADMIN(2), ADMIN(3);

        private int privilegeLevel;

        public boolean hasPrivilegeOf(Privileges privilege) {
            return this.privilegeLevel >= privilege.getPrivilegeLevel();
        }

        public int getPrivilegeLevel() {
            return this.privilegeLevel;
        }

        Privileges(int privilegeLevel) {
            this.privilegeLevel = privilegeLevel;
        }
    }

    private class UserInfo {
        public String username;
        public Privileges privilege;

        public UserInfo(String username, Privileges privilege) {
            this.username = username;
            this.privilege = privilege;
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
        this.actorToUserInfo.put(groupCreatorActor, new UserInfo(groupCreatorUsername, Privileges.ADMIN));
    }

    @Override
    public Receive createReceive() {
        return this.receiverBuilder;
    }

    private void onLeaveGroupRequest(LeaveGroupRequest leaveGroupRequest) {
        // TODO: Delete
        if (!this.groupName.equals(leaveGroupRequest.groupName)) {
            System.out.println("Manager did something wrong");
            return;
        }

        if (!this.actorToUserInfo.containsKey(getSender())) {
            String errorMessage = String.format("User %s is not in group %s", leaveGroupRequest.leavingUsername, leaveGroupRequest.groupName);
            System.out.println(errorMessage);
            getSender().tell(new CommandFailure(errorMessage), getSelf());
            return;
        }

        UserInfo leavingUser = this.actorToUserInfo.get(getSender());
        this.router.route(new GroupTextMessage(this.groupName, leavingUser.username,
                        String.format("%s has left %s!", leavingUser.username, this.groupName)),
                this.getSelf());

        if (leavingUser.privilege.hasPrivilegeOf(Privileges.ADMIN)) {
            this.router.route(new GroupTextMessage(this.groupName, leavingUser.username,
                            String.format("admin has closed %s!", this.groupName)),
                    this.getSelf());
            getContext().stop(getSelf());
            return;
        }

        this.router = this.router.removeRoutee(getSender());
        this.actorToUserInfo.remove(getSender());

        if (leavingUser.privilege.hasPrivilegeOf(Privileges.CO_ADMIN)) {
            // TODO: Remove from co-admin list
        }
    }

    private void OnGroupSendText(TextMessage message) {
        if (validateUserInGroup()) {
            // TODO: Validate Privileges
            router.route(new GroupTextMessage(this.groupName, message.sender, message.message), getSender());
        }
    }

    private void OnGroupSendFile(BinaryMessage message) {
        if (validateUserInGroup()) {
            // TODO: Validate Privileges
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
        // TODO
    }
}