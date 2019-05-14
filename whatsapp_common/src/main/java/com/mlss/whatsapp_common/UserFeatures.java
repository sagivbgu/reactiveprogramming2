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
        public ConnectionAccepted() {
        }
    }

    static public class ConnectionDenied implements Serializable {
        public ConnectionDenied() {
        }
    }

    static public class DisconnectRequest implements Serializable {
        public DisconnectRequest() {
        }
    }

    public UserFeatures() {
    }
}
