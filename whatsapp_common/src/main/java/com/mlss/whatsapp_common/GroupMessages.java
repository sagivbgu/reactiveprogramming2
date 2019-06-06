package com.mlss.whatsapp_common;

import java.io.Serializable;
import com.mlss.whatsapp_common.ManagerCommands.*;

public class GroupMessages {
    static public class GroupTextMessage implements Serializable {
        public final String groupName;
        public final String senderUsername;
        public final String message;

        public GroupTextMessage(String groupName, String senderUsername, String message) {
            this.groupName = groupName;
            this.senderUsername = senderUsername;
            this.message = message;
        }
    }

    static public class GroupBinaryMessage implements Serializable {
        public final String groupName;
        public final BinaryMessage message;

        public GroupBinaryMessage(String groupName, BinaryMessage message) {
            this.groupName = groupName;
            this.message = message;
        }
    }

    static public class GroupInviteUserCommand implements Serializable {
        public final String groupName;
        public final String invitedUsername;
        public String invitedUserAddress;

        public GroupInviteUserCommand(String groupName, String invitedUsername) {
            this.groupName = groupName;
            this.invitedUsername = invitedUsername;
            this.invitedUserAddress = null;
        }
    }

    static public class GroupInviteMessage implements Serializable {
        public final String groupName;
        public final String inviterUsername;

        public GroupInviteMessage(String groupName, String inviterUsername) {
            this.groupName = groupName;
            this.inviterUsername = inviterUsername;
        }
    }

    static public class GroupInviteResponse implements Serializable {
        public final String response;
        public String invitedUsername;

        public GroupInviteResponse(String response) {
            this.response = response;
            this.invitedUsername = null;
        }
    }

    static public class GroupRemoveUserCommand implements Serializable {
        public final String groupName;
        public final String userToRemove;
        public String removedUserAddress;

        public GroupRemoveUserCommand(String groupName, String userToRemove) {
            this.groupName = groupName;
            this.userToRemove = userToRemove;
            this.removedUserAddress = null;
        }
    }

    static public class MuteUserCommand implements Serializable {
        public final String groupName;
        public final String mutedUsername;
        public final double timeInSeconds;

        public MuteUserCommand(String groupName, String invitedUsername, double timeInSeconds) {
            this.groupName = groupName;
            this.mutedUsername = invitedUsername;
            this.timeInSeconds = timeInSeconds;
        }
    }

    public GroupMessages() {
    }
}
