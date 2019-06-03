package com.mlss.whatsapp_common;

import java.io.Serializable;

public class ManagerCommands {
    static public class UserAddressRequest implements Serializable {
        public final String username;

        public UserAddressRequest(String username) {
            this.username = username;
        }
    }

    static public class UserAddressResponse implements Serializable {
        public final String username;
        public final String address;

        public UserAddressResponse(String username, String address) {
            this.username = username;
            this.address = address;
        }
    }

    static public class UserNotFound implements Serializable {
        public final String username;

        public UserNotFound(String username) {
            this.username = username;
        }
    }

    static public class CreateGroup implements Serializable {
        public final String groupName;

        public CreateGroup(String groupName) {
            this.groupName = groupName;
        }
    }

    static public class GroupSendText implements Serializable {
        public final String groupName;
        public final String message;

        public GroupSendText(String groupName, String message) {
            this.groupName = groupName;
            this.message = message;
        }
    }

    public ManagerCommands() {
    }
}
