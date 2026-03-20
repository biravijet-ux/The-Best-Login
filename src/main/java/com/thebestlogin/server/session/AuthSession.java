package com.thebestlogin.server.session;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.thebestlogin.auth.AuthScreenMode;
import com.thebestlogin.util.HashingUtil;

public final class AuthSession {
    private boolean registered;
    private boolean authorized;
    private long deadlineAtMillis;
    private String challenge;
    private long challengeIssuedAtMillis;
    private AuthScreenMode promptMode;
    private int currentHashVersion;
    private String currentSalt;
    private int targetHashVersion;
    private String targetSalt;
    private int failedAttempts;
    private ResourceKey<Level> lockDimension;
    private double lockX;
    private double lockY;
    private double lockZ;
    private float lockYaw;
    private float lockPitch;
    private ResourceKey<Level> returnDimension;
    private double returnX;
    private double returnY;
    private double returnZ;
    private float returnYaw;
    private float returnPitch;
    private boolean holdingAreaActive;
    private boolean originalMayfly;
    private boolean originalFlying;
    private boolean originalInvulnerable;

    public AuthSession(boolean registered, long deadlineAtMillis, ServerPlayer player) {
        restart(registered, deadlineAtMillis, player);
    }

    public void restart(boolean registered, long deadlineAtMillis, ServerPlayer player) {
        boolean preserveReturnPosition = holdingAreaActive && !authorized && returnDimension != null;
        boolean preserveOriginalAbilities = holdingAreaActive && !authorized;

        this.registered = registered;
        this.authorized = false;
        this.deadlineAtMillis = deadlineAtMillis;
        this.challenge = null;
        this.challengeIssuedAtMillis = 0L;
        this.promptMode = registered ? AuthScreenMode.LOGIN : AuthScreenMode.REGISTER;
        this.currentHashVersion = HashingUtil.CURRENT_HASH_VERSION;
        this.currentSalt = "";
        this.targetHashVersion = HashingUtil.CURRENT_HASH_VERSION;
        this.targetSalt = "";
        this.failedAttempts = 0;
        this.holdingAreaActive = false;
        if (!preserveReturnPosition) {
            captureReturnPosition(player);
        }
        if (!preserveOriginalAbilities) {
            captureOriginalAbilities(player);
        }
        captureLock(player);
    }

    public void captureLock(ServerPlayer player) {
        captureLock(player.serverLevel().dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    public void captureLock(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
        this.lockDimension = dimension;
        this.lockX = x;
        this.lockY = y;
        this.lockZ = z;
        this.lockYaw = yaw;
        this.lockPitch = pitch;
    }

    public void captureReturnPosition(ServerPlayer player) {
        this.returnDimension = player.serverLevel().dimension();
        this.returnX = player.getX();
        this.returnY = player.getY();
        this.returnZ = player.getZ();
        this.returnYaw = player.getYRot();
        this.returnPitch = player.getXRot();
    }

    public void captureOriginalAbilities(ServerPlayer player) {
        this.originalMayfly = player.getAbilities().mayfly;
        this.originalFlying = player.getAbilities().flying;
        this.originalInvulnerable = player.getAbilities().invulnerable;
    }

    public void preparePrompt(AuthScreenMode mode, int currentHashVersion, String currentSalt, int targetHashVersion, String targetSalt, String challenge, long now) {
        this.promptMode = mode;
        this.currentHashVersion = currentHashVersion;
        this.currentSalt = currentSalt == null ? "" : currentSalt;
        this.targetHashVersion = targetHashVersion;
        this.targetSalt = targetSalt == null ? "" : targetSalt;
        this.challenge = challenge;
        this.challengeIssuedAtMillis = now;
    }

    public boolean consumeChallenge(String value, long now, long ttlMillis) {
        boolean valid = challenge != null
                && challenge.equals(value)
                && (ttlMillis == Long.MAX_VALUE || now - challengeIssuedAtMillis <= ttlMillis);
        clearPrompt();
        return valid;
    }

    public void clearPrompt() {
        this.challenge = null;
        this.challengeIssuedAtMillis = 0L;
        this.failedAttempts = 0;
    }

    public boolean hasActivePrompt() {
        return challenge != null && !challenge.isBlank();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void authorize() {
        this.authorized = true;
        clearPrompt();
        this.holdingAreaActive = false;
    }

    public long getDeadlineAtMillis() {
        return deadlineAtMillis;
    }

    public void setDeadlineAtMillis(long deadlineAtMillis) {
        this.deadlineAtMillis = deadlineAtMillis;
    }

    public String getChallenge() {
        return challenge;
    }

    public AuthScreenMode getPromptMode() {
        return promptMode;
    }

    public int getCurrentHashVersion() {
        return currentHashVersion;
    }

    public String getCurrentSalt() {
        return currentSalt;
    }

    public int getTargetHashVersion() {
        return targetHashVersion;
    }

    public String getTargetSalt() {
        return targetSalt;
    }

    public int incrementFailedAttempts() {
        failedAttempts++;
        return failedAttempts;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public ResourceKey<Level> getLockDimension() {
        return lockDimension;
    }

    public double getLockX() {
        return lockX;
    }

    public double getLockY() {
        return lockY;
    }

    public double getLockZ() {
        return lockZ;
    }

    public float getLockYaw() {
        return lockYaw;
    }

    public float getLockPitch() {
        return lockPitch;
    }

    public ResourceKey<Level> getReturnDimension() {
        return returnDimension;
    }

    public double getReturnX() {
        return returnX;
    }

    public double getReturnY() {
        return returnY;
    }

    public double getReturnZ() {
        return returnZ;
    }

    public float getReturnYaw() {
        return returnYaw;
    }

    public float getReturnPitch() {
        return returnPitch;
    }

    public boolean isHoldingAreaActive() {
        return holdingAreaActive;
    }

    public void setHoldingAreaActive(boolean holdingAreaActive) {
        this.holdingAreaActive = holdingAreaActive;
    }

    public boolean isOriginalMayfly() {
        return originalMayfly;
    }

    public boolean isOriginalFlying() {
        return originalFlying;
    }

    public boolean isOriginalInvulnerable() {
        return originalInvulnerable;
    }
}
