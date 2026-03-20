package com.thebestlogin.client;

import com.thebestlogin.auth.AuthMessageType;
import com.thebestlogin.auth.AuthScreenMode;

public record AuthPromptData(
        AuthScreenMode mode,
        String nickname,
        String serverId,
        String challenge,
        int currentHashVersion,
        String currentSalt,
        int targetHashVersion,
        String targetSalt,
        String publicKey,
        String statusMessage,
        AuthMessageType statusType,
        long deadlineAtMillis
) {
    public int secondsRemaining() {
        long delta = deadlineAtMillis - System.currentTimeMillis();
        if (delta <= 0L) {
            return 0;
        }
        return (int) ((delta + 999L) / 1000L);
    }
}