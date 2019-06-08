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
        public MuteInfo muteInfo;

        public UserInfo(String username, Privileges privilege) {
            this.username = username;
            this.privilege = privilege;
            this.muteInfo = null;
        }
    }

    private class MuteInfo {
        public long timeInSeconds;
        public Cancellable muteCancellable;

        public MuteInfo(long timeInSeconds, Cancellable muteCancellable) {
            this.timeInSeconds = timeInSeconds;
            this.muteCancellable = muteCancellable;
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
                .match(DisconnectRequest.class, this::onDisconnectRequest)
                .match(GroupInviteUserCommand.class, this::onGroupInviteUserCommand)
                .match(GroupRemoveUserCommand.class, this::onGroupRemoveUserCommand)
                .match(GroupInviteResponse.class, this::onGroupInviteResponse)
                .match(TextMessage.class, this::OnGroupSendText)
                .match(BinaryMessage.class, this::OnGroupSendFile)
                .match(MuteUserCommand.class, this::onMuteUserCommand)
                .match(UnmuteUserCommand.class, this::onUnmuteUserCommand)
                .match(CoadminAddRequest.class, this::onCoadminAddRequest)
                .match(CoadminRemoveRequest.class, this::onCoadminRemoveRequest)
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
        if (!validateSenderInGroup() || !validateSenderIsCoAdmin()) {
            return;
        }

        if (inviteUserCommand.invitedUserAddress == null) {
            getSender().tell(
                    new GeneralMessage(String.format("%s does not exist!", inviteUserCommand.invitedUsername)), getSelf());
            return;
        }

        if (getUserInfoByUserAddress(inviteUserCommand.invitedUserAddress) != null) {
            getSender().tell(
                    new GeneralMessage(String.format("%s is already in %s!", inviteUserCommand.invitedUsername, this.groupName)), getSelf());
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

            getSender().tell(new GeneralMessage(String.format("Welcome to %s!", this.groupName)), getSelf());
        }
    }

    private void onGroupRemoveUserCommand(GroupRemoveUserCommand removeUserCommand) {
        if (!validateSenderInGroup() || !validateSenderIsCoAdmin()) {
            return;
        }

        ActorRef removedUserActor = getUserActorByName(removeUserCommand.userToRemove);
        if (removedUserActor == null) {
            String errorMessage = String.format("%s is not in %s!", removeUserCommand.userToRemove, this.groupName);
            getSender().tell(new GeneralMessage(errorMessage), getSelf());
            return;
        }

        if (removedUserActor.equals(this.groupCreatorActor)) {
            getSender().tell(new GeneralMessage("You can't remove the admin!"), getSelf());
            return;
        }

        UserInfo removerUserInfo = this.actorToUserInfo.get(getSender());
        this.actorToUserInfo.remove(removedUserActor);
        this.router = this.router.removeRoutee(removedUserActor);

        String message = String.format("You have been removed from %s by %s!", this.groupName, removerUserInfo.username);
        removedUserActor.tell(new GeneralMessage(message, removerUserInfo.username, this.groupName), getSelf());
    }

    private void leaveGroup(ActorRef userActor) {
        this.router = this.router.removeRoutee(userActor);

        UserInfo leavingUser = this.actorToUserInfo.get(userActor);
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

        this.actorToUserInfo.remove(userActor);
    }

    private void onLeaveGroupRequest(LeaveGroupRequest leaveGroupRequest) {
        if (!this.actorToUserInfo.containsKey(getSender())) {
            String errorMessage = String.format("%s is not in %s!", leaveGroupRequest.leavingUsername, this.groupName);
            System.out.println(errorMessage);
            getSender().tell(new GeneralMessage(errorMessage), getSelf());
            return;
        }

        this.leaveGroup(getSender());
    }

    private void onDisconnectRequest(DisconnectRequest request) {
        this.leaveGroup(getSender());
    }

    private void OnGroupSendText(TextMessage message) {
        if (validateSenderInGroup() && validateSenderNotMuted()) {
            router.route(new GroupTextMessage(this.groupName, message.sender, message.message), getSender());
        }
    }

    private void OnGroupSendFile(BinaryMessage message) {
        if (validateSenderInGroup() && validateSenderNotMuted()) {
            router.route(new GroupBinaryMessage(this.groupName, message), getSender());
        }
    }

    private void onMuteUserCommand(MuteUserCommand muteUserCommand) {
        if (!validateSenderInGroup()
                || !validateTargetInGroup(muteUserCommand.mutedUsername)
                || !validateSenderIsCoAdmin()
                || !validateNotTargetingItself(muteUserCommand.mutedUsername, "You can't mute yourself!")) {
            return;
        }

        ActorRef mutedUserActor = getUserActorByName(muteUserCommand.mutedUsername);
        if (mutedUserActor.equals(this.groupCreatorActor)) {
            getSender().tell(new GeneralMessage("You can't mute the group admin"), getSelf());
            return;
        }

        UserInfo mutedUserInfo = this.actorToUserInfo.get(mutedUserActor);
        if (mutedUserInfo.muteInfo != null) {
            mutedUserInfo.muteInfo.muteCancellable.cancel();
        }

        mutedUserInfo.privilege = Privileges.MUTED_USER;

        String muterUsername = this.actorToUserInfo.get(getSender()).username;
        mutedUserActor.tell(
                new GroupTextMessage(this.groupName, muterUsername,
                        String.format("You have been muted for %s seconds in %s by %s!",
                                muteUserCommand.timeInSeconds, this.groupName, muterUsername)),
                getSender());


        ActorSystem system = getContext().getSystem();
        Cancellable muteCancellable = system.scheduler().scheduleOnce(
                Duration.ofSeconds(muteUserCommand.timeInSeconds),
                () -> unmuteUser(mutedUserActor, mutedUserInfo, muterUsername, "Muting time is up!"),
                system.dispatcher());
        mutedUserInfo.muteInfo = new MuteInfo(muteUserCommand.timeInSeconds, muteCancellable);
    }

    private boolean validateNotTargetingItself(String targetUsername, String errorMessage) {
        if (this.actorToUserInfo.get(getSender()).username.equals(targetUsername)) {
            getSender().tell(new GeneralMessage(errorMessage), getSelf());
            return false;
        }
        return true;
    }

    private void onUnmuteUserCommand(UnmuteUserCommand unmuteUserCommand) {
        if (!validateSenderInGroup() || !validateTargetInGroup(unmuteUserCommand.unmutedUsername) || !validateSenderIsCoAdmin()) {
            return;
        }

        ActorRef mutedUserActor = getUserActorByName(unmuteUserCommand.unmutedUsername);
        if (this.actorToUserInfo.get(mutedUserActor).privilege.hasPrivilegeOf(Privileges.USER)) {
            getSender().tell(
                    new GeneralMessage(String.format("%s is not muted!", unmuteUserCommand.unmutedUsername)),
                    getSelf());
            return;
        }
        UserInfo mutedUserInfo = this.actorToUserInfo.get(mutedUserActor);
        String muterUsername = this.actorToUserInfo.get(getSender()).username;

        mutedUserInfo.muteInfo.muteCancellable.cancel();
        unmuteUser(mutedUserActor, mutedUserInfo, muterUsername, "");
    }

    private void unmuteUser(ActorRef mutedUserActor, UserInfo mutedUserInfo, String unmuterUsername, String unmutingReason) {
        mutedUserInfo.privilege = Privileges.USER;
        mutedUserActor.tell(
                new GroupTextMessage(this.groupName, unmuterUsername,
                        "You have been unmuted! " + unmutingReason),
                getSender());
        mutedUserInfo.muteInfo = null;
    }

    private void onCoadminAddRequest(CoadminAddRequest coadminAddRequest) {
        if (!validateSenderInGroup() || !validateTargetInGroup(coadminAddRequest.coadminUsername)
                || !validateSenderIsCoAdmin() || !validateNotTargetingItself(coadminAddRequest.coadminUsername,
                "You can't add yourself as a co-admin")) {
            return;
        }

        ActorRef coadminUserActor = getUserActorByName(coadminAddRequest.coadminUsername);
        if (coadminUserActor.equals(this.groupCreatorActor)) {
            getSender().tell(new GeneralMessage("You can't add the admin as co-admin!"), getSelf());
            return;
        }

        UserInfo coadminUserInfo = this.actorToUserInfo.get(coadminUserActor);
        if (coadminUserInfo.muteInfo != null) {
            coadminUserInfo.muteInfo.muteCancellable.cancel();
            unmuteUser(coadminUserActor, coadminUserInfo, this.actorToUserInfo.get(getSender()).username, "");
        }
        coadminUserInfo.privilege = Privileges.CO_ADMIN;

        coadminUserActor.tell(
                new GeneralMessage(String.format("You have been promoted to co-admin in %s!", this.groupName)),
                getSender());
    }

    private void onCoadminRemoveRequest(CoadminRemoveRequest coadminRemoveRequest) {
        if (!validateSenderInGroup() || !validateTargetInGroup(coadminRemoveRequest.coadminUsername)
                || !validateSenderIsCoAdmin() || !validateNotTargetingItself(coadminRemoveRequest.coadminUsername,
                "You can't demote yourself from being a co-admin")) {
            return;
        }

        ActorRef coadminUserActor = getUserActorByName(coadminRemoveRequest.coadminUsername);
        if (coadminUserActor.equals(this.groupCreatorActor)) {
            getSender().tell(new GeneralMessage("You can't remove the admin from being co-admin!"), getSelf());
            return;
        }
        UserInfo coadminUserInfo = this.actorToUserInfo.get(coadminUserActor);

        if (!coadminUserInfo.privilege.hasPrivilegeOf(Privileges.CO_ADMIN)) {
            getSender().tell(new GeneralMessage(String.format("You can't demote a non-co-admin user %s",
                    coadminRemoveRequest.coadminUsername)),
                    getSender());
            return;
        }

        coadminUserInfo.privilege = Privileges.USER;
        coadminUserActor.tell(
                new GeneralMessage(String.format("You have been demoted to user in %s!", this.groupName)),
                getSender());
    }

    private boolean validateTargetInGroup(String username) {
        ActorRef targetUserActor = getUserActorByName(username);
        if (targetUserActor == null) {
            getSender().tell(new GeneralMessage(String.format("%s does not exist!", username)),
                    getSelf());
            return false;
        }
        return true;
    }

    private boolean validateSenderInGroup() {
        if (!this.actorToUserInfo.containsKey(getSender())) {
            getSender().tell(new GeneralMessage(String.format("You are not part of %s!", this.groupName)), getSelf());
            return false;
        }
        return true;
    }

    private boolean validateSenderNotMuted() {
        UserInfo userInfo = this.actorToUserInfo.get(getSender());
        if (!userInfo.privilege.hasPrivilegeOf(Privileges.USER)) {
            getSender().tell(
                    new GeneralMessage(String.format("You are muted for %s seconds in %s",
                            userInfo.muteInfo.timeInSeconds, this.groupName)),
                    getSelf());
            return false;
        }
        return true;
    }

    private boolean validateSenderIsCoAdmin() {
        UserInfo userInfo = this.actorToUserInfo.get(getSender());
        if (!userInfo.privilege.hasPrivilegeOf(Privileges.CO_ADMIN)) {
            getSender().tell(
                    new GeneralMessage(String.format("You are neither an admin nor a co-admin of %s!", this.groupName)),
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
        ActorRef userActor = t.getActor();
        this.leaveGroup(userActor);
        this.router = this.router.removeRoutee(userActor);
    }
}