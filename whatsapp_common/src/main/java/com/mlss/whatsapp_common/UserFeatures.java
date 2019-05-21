package com.mlss.whatsapp_common;

import java.io.Serializable;

public class UserFeatures {

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

        public ConnectionDenied(String accepted_username) {
            this.deniedUsername = accepted_username;
        }
    }

    static public class DisconnectRequest implements Serializable {
        public DisconnectRequest() {
        }
    }

    public UserFeatures() {
    }
}
