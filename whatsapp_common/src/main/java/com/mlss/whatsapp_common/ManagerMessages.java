package com.mlss.whatsapp_common;

import java.io.Serializable;

public class ManagerMessages {
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

    static public class ConnectRequest implements Serializable {
        public final String username;

        public ConnectRequest(String username) {
            this.username = username;
        }
    }

    static public class ConnectionAccepted implements Serializable {
        public final String acceptedUsername;

        public ConnectionAccepted(String acceptedUsername) {
            this.acceptedUsername = acceptedUsername;
        }
    }

    static public class ConnectionDenied implements Serializable {
        public final String deniedUsername;

        public ConnectionDenied(String acceptedUsername) {
            this.deniedUsername = acceptedUsername;
        }
    }

    static public class DisconnectRequest implements Serializable {
        public DisconnectRequest() {
        }
    }

    static public class DisconnectAccepted implements Serializable {
        public final String disconnectedUsername;

        public DisconnectAccepted(String disconnectedUsername) {
            this.disconnectedUsername = disconnectedUsername;
        }
    }

    static public class GeneralMessage implements Serializable {
        public final String message;
        public final String groupName;
        public final String sourceUsername;

        public GeneralMessage(String message) {
            this.message = message;
            this.sourceUsername = null;
            this.groupName = null;
        }

        public GeneralMessage(String message, String sourceUsername, String groupName) {
            this.message = message;
            this.sourceUsername = sourceUsername;
            this.groupName = groupName;
        }
    }

    static public class CreateGroupRequest implements Serializable {
        public final String groupName;

        public CreateGroupRequest(String groupName) {
            this.groupName = groupName;
        }
    }

    static abstract public class Message implements Serializable {
        public String sender;

        public Message() {
        }
    }

    static public class TextMessage extends Message implements Serializable {
        public final String message;

        public TextMessage(String message) {
            super();
            this.message = message;
        }
    }

    static public class BinaryMessage extends Message implements Serializable {
        public final byte[] message;
        public final String fileName;

        public BinaryMessage(byte[] message, String fileName) {
            super();
            this.message = message;
            this.fileName = fileName;
        }
    }

    static public class GroupSendMessage implements Serializable {
        public final String groupName;
        public final Message message;

        public GroupSendMessage(String groupName, Message message) {
            this.groupName = groupName;
            this.message = message;
        }
    }

    static public class SendMessageRequest implements Serializable {
        public final String target;
        public final Message message;

        public SendMessageRequest(String target, Message message) {
            this.target = target;
            this.message = message;
        }
    }

    public ManagerMessages() {
    }
}
