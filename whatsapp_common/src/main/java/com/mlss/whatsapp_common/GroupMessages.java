package com.mlss.whatsapp_common;

import java.io.Serializable;

public class GroupMessages {
    static public class NewGroupText implements Serializable {
        public final String groupName;
        public final String senderUsername;
        public final String message;

        public NewGroupText(String groupName, String senderUsername, String message) {
            this.groupName = groupName;
            this.senderUsername = senderUsername;
            this.message = message;
        }
    }

    public GroupMessages() {
    }
}
