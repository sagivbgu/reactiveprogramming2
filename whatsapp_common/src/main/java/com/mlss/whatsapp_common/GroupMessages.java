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

    static public class InviteResponse implements Serializable {
        public final String response;

        public InviteResponse(String response) {
            this.response = response;
        }
    }

    public GroupMessages() {
    }
}
