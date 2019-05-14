package com.mlss.whatsapp_common;

public class UserFeatures {

    static public class ConnectRequest {
        public final String username;

        public ConnectRequest(String username) {
            this.username = username;
        }
    }

    static public class ConnectionAccepted {
        public ConnectionAccepted() {
        }
    }

    static public class ConnectionDenied {
        public ConnectionDenied() {
        }
    }

    static public class DisconnectRequest {
        public DisconnectRequest() {
        }
    }

    public UserFeatures() {
    }
}
