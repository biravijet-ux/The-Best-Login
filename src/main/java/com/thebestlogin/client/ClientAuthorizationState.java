package com.thebestlogin.client;

import com.thebestlogin.auth.AuthScreenMode;

public final class ClientAuthorizationState {
    private static volatile boolean authenticated = true;
    private static volatile AuthPromptData promptData;
    private static volatile long autoLoginGraceUntilMillis;

    private ClientAuthorizationState() {
    }

    public static boolean isAuthenticated() {
        return authenticated;
    }

    public static void setAuthenticated(boolean value) {
        authenticated = value;
        if (value) {
            promptData = null;
            autoLoginGraceUntilMillis = 0L;
        }
    }

    public static void setPromptData(AuthPromptData value, boolean waitForAutoLogin) {
        promptData = value;
        autoLoginGraceUntilMillis = waitForAutoLogin ? System.currentTimeMillis() + 1_200L : 0L;
    }

    public static AuthPromptData getPromptData() {
        return promptData;
    }

    public static void clearPrompt() {
        promptData = null;
        autoLoginGraceUntilMillis = 0L;
    }

    public static boolean shouldOpenPromptScreen() {
        AuthPromptData currentPrompt = promptData;
        if (currentPrompt == null) {
            return false;
        }
        boolean promptRequiresScreen = !authenticated || currentPrompt.mode() == AuthScreenMode.CHANGE_PASSWORD;
        return promptRequiresScreen && System.currentTimeMillis() >= autoLoginGraceUntilMillis;
    }

    public static void clearAutoLoginDelay() {
        autoLoginGraceUntilMillis = 0L;
    }

    public static void reset() {
        authenticated = true;
        promptData = null;
        autoLoginGraceUntilMillis = 0L;
    }
}
