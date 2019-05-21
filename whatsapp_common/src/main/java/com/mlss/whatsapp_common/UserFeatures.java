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
        public final String accepted_username;

        public ConnectionAccepted(String accepted_username) {
            this.accepted_username = accepted_username;
        }
    }

    static public class ConnectionDenied implements Serializable {
        public final String denied_username;

        public ConnectionDenied(String accepted_username) {
            this.denied_username = accepted_username;
        }
    }

    static public class DisconnectRequest implements Serializable {
        public DisconnectRequest() {
        }
    }

    public UserFeatures() {
    }
}
