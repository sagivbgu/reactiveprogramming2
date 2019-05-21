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
        public final String address;

        public UserAddressResponse(String address) {
            this.address = address;
        }
    }

    static public class UserNotFound implements Serializable {
        public final String username;

        public UserNotFound(String username) {
            this.username = username;
        }
    }

    public ManagerCommands() {
    }
}
