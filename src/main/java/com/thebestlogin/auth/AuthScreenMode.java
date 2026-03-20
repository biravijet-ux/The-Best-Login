package com.thebestlogin.auth;

public enum AuthScreenMode {
    LOGIN(0),
    REGISTER(1),
    CHANGE_PASSWORD(2);

    private final int id;

    AuthScreenMode(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static AuthScreenMode fromId(int id) {
        for (AuthScreenMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return LOGIN;
    }
}