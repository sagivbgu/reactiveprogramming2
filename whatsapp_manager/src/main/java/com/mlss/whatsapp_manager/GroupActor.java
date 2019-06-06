package com.mlss.whatsapp_manager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import akka.actor.*;
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
        public long mutedTimeInSeconds;

        public UserInfo(String username, Privileges privilege) {
            this.username = username;
            this.privilege = privilege;
            this.mutedTimeInSeconds = 0;
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
                .match(GroupInviteUserCommand.class, this::onGroupInviteUserCommand)
                .match(GroupInviteResponse.class, this::onGroupInviteResponse)
                .match(TextMessage.class, this::OnGroupSendText)
                .match(BinaryMessage.class, this::OnGroupSendFile)
                .match(MuteUserCommand.class, this::onMuteUserCommand)
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

    private void onGroupInviteUserCommand(GroupInviteUserCommand inviteUserCommand) {
        if (!validateUserInGroup(getSender()) && !validateUserIsAdmin(getSender())) {
            return;
        }

        if (inviteUserCommand.invitedUserAddress == null) {
            getSender().tell(
                    new CommandFailure(String.format("%s does not exist!", inviteUserCommand.invitedUsername)), getSelf());
            return;
        }

        if (getUserInfoByUserAddress(inviteUserCommand.invitedUserAddress) != null) {
            getSender().tell(
                    new CommandFailure(String.format("%s is already in %s!", inviteUserCommand.invitedUsername, this.groupName)), getSelf());
            return;
        }

        UserInfo inviterUserInfo = this.actorToUserInfo.get(getSender());
        ActorSelection invitedUserActor = getContext().actorSelection(inviteUserCommand.invitedUserAddress);
        invitedUserActor.tell(new GroupInviteMessage(this.groupName, inviterUserInfo.username), getSelf());
    }

    private void onGroupInviteResponse(GroupInviteResponse inviteResponse) {
        if (inviteResponse.response.equals("Yes")) {
            this.actorToUserInfo.put(getSender(), new UserInfo(inviteResponse.invitedUsername, Privileges.USER));
            this.router = this.router.addRoutee(getSender());
            getContext().watch(getSender());

            getSender().tell(new CommandFailure(String.format("Welcome to %s!", this.groupName)), getSelf());
        }
    }

    private void onLeaveGroupRequest(LeaveGroupRequest leaveGroupRequest) {
        // TODO: Delete
        if (!this.groupName.equals(leaveGroupRequest.groupName)) {
            System.out.println("Manager did something wrong");
            return;
        }

        if (!this.actorToUserInfo.containsKey(getSender())) {
            String errorMessage = String.format("%s is not in %s!", leaveGroupRequest.leavingUsername, leaveGroupRequest.groupName);
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
        if (validateUserInGroup(getSender()) && validateUserIsNotMuted(getSender())) {
            router.route(new GroupTextMessage(this.groupName, message.sender, message.message), getSender());
        }
    }

    private void OnGroupSendFile(BinaryMessage message) {
        if (validateUserInGroup(getSender()) && validateUserIsNotMuted(getSender())) {
            router.route(new GroupBinaryMessage(this.groupName, message), getSender());
        }
    }

    private void onMuteUserCommand(MuteUserCommand muteUserCommand) {
        if (!validateUserInGroup(getSender())) {
            return;
        }

        ActorRef mutedUserActor = getUserActorByName(muteUserCommand.mutedUsername);
        if (mutedUserActor == null) {
            getSender().tell(new CommandFailure(String.format("%s does not exist!", muteUserCommand.mutedUsername)),
                    getSelf());
            return;
        }

        if (!validateUserIsAdmin(getSender())) {
            return;
        }

        UserInfo mutedUserInfo = this.actorToUserInfo.get(mutedUserActor);
        mutedUserInfo.privilege = Privileges.MUTED_USER;

        String muterUsername = this.actorToUserInfo.get(getSender()).username;
        mutedUserActor.tell(
                new GroupTextMessage(this.groupName, muterUsername,
                        String.format("You have been muted for %s in %s by %s!",
                                muteUserCommand.timeInSeconds, this.groupName, muterUsername)),
                getSender());


        ActorSystem system = getContext().getSystem();
        system.scheduler().scheduleOnce(
                Duration.ofSeconds(muteUserCommand.timeInSeconds),
                () -> {
                    mutedUserInfo.privilege = Privileges.USER;
                    mutedUserActor.tell(
                            new GroupTextMessage(this.groupName, muterUsername,
                                    "You have been unmuted! Muting time is up!"),
                            getSender());
                },
                system.dispatcher());
        mutedUserInfo.mutedTimeInSeconds = muteUserCommand.timeInSeconds;
    }

    private boolean validateUserInGroup(ActorRef userActor) {
        if (!this.actorToUserInfo.containsKey(userActor)) {
            userActor.tell(new CommandFailure(String.format("You are not part of %s!", this.groupName)), getSelf());
            return false;
        }
        return true;
    }

    private boolean validateUserIsNotMuted(ActorRef userActor) {
        UserInfo userInfo = this.actorToUserInfo.get(userActor);
        if (!userInfo.privilege.hasPrivilegeOf(Privileges.USER)) {
            userActor.tell(
                    new CommandFailure(String.format("You are muted for %s seconds in %s",
                            userInfo.mutedTimeInSeconds, this.groupName)),
                    getSelf());
            return false;
        }
        return true;
    }

    private boolean validateUserIsAdmin(ActorRef userActor) {
        UserInfo userInfo = this.actorToUserInfo.get(userActor);
        if (!userInfo.privilege.hasPrivilegeOf(Privileges.CO_ADMIN)) {
            userActor.tell(
                    new CommandFailure(String.format("You are neither an admin nor a co-admin of %s!", this.groupName)),
                    getSelf());
            return false;
        }
        return true;
    }

    private UserInfo getUserInfoByUserAddress(String userAddress) {
        for (Map.Entry<ActorRef, UserInfo> entry : this.actorToUserInfo.entrySet()) {
            if (entry.getKey().path().toString().equals(userAddress)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private ActorRef getUserActorByName(String userName) {
        for (Map.Entry<ActorRef, UserInfo> entry : this.actorToUserInfo.entrySet()) {
            if (userName.equals(entry.getValue().username)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void onTerminated(Terminated t) {
        this.router = this.router.removeRoutee(t.getActor());
        // TODO
    }
}