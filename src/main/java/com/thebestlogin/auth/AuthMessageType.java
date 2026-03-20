package com.thebestlogin.auth;

public enum AuthMessageType {
    NONE(0),
    INFO(1),
    SUCCESS(2),
    ERROR(3);

    private final int id;

    AuthMessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static AuthMessageType fromId(int id) {
        for (AuthMessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }
}