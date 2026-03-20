package com.thebestlogin.server;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.thebestlogin.auth.AuthMessageType;
import com.thebestlogin.auth.AuthScreenMode;
import com.thebestlogin.network.TheBestLoginNetwork;
import com.thebestlogin.network.packet.AuthPromptS2CPacket;
import com.thebestlogin.network.packet.AuthStateS2CPacket;
import com.thebestlogin.network.packet.LocalPasswordSyncS2CPacket;
import com.thebestlogin.server.session.AuthSession;
import com.thebestlogin.server.storage.PlayerPasswordStore;
import com.thebestlogin.server.storage.ServerConfigStore;
import com.thebestlogin.util.HashingUtil;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TheBestLoginServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TheBestLoginServer INSTANCE = new TheBestLoginServer();
    private static final long CHALLENGE_TTL_MILLIS = 3_600_000L;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_PASSWORD_LENGTH = 40;
    private static final int HOLDING_GRID_SIZE = 5;
    private static final int HOLDING_SLOT_SPACING = 6;
    private static final int HOLDING_HEIGHT_OFFSET = 72;

    private final ServerConfigStore configStore = new ServerConfigStore();
    private final PlayerPasswordStore playerStore = new PlayerPasswordStore();
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();

    private KeyPair transportKeyPair;
    private String transportPublicKey = "";

    private TheBestLoginServer() {
    }

    public static TheBestLoginServer get() {
        return INSTANCE;
    }

    public void onServerStarting() {
        configStore.loadOrCreate();
        playerStore.loadOrCreate();
        sessions.clear();
        transportKeyPair = HashingUtil.generateTransportKeyPair();
        transportPublicKey = HashingUtil.encodePublicKey(transportKeyPair.getPublic());
    }

    public void onServerStopping() {
        sessions.clear();
    }

    public void onPlayerLogin(ServerPlayer player) {
        if (!player.server.isDedicatedServer()) {
            sessions.remove(player.getUUID());
            if (TheBestLoginNetwork.canSendTo(player)) {
                syncClientAuthorization(player, true, playerStore.isRegistered(player.getGameProfile().getName()));
            }
            return;
        }
        if (!TheBestLoginNetwork.canSendTo(player)) {
            player.connection.disconnect(Component.literal("Для входа на этот сервер нужен клиентский мод The Best Login.").withStyle(ChatFormatting.RED));
            return;
        }

        boolean registered = playerStore.isRegistered(player.getGameProfile().getName());
        AuthSession session = new AuthSession(registered, nextDeadline(), player);
        sessions.put(player.getUUID(), session);
        moveToHoldingArea(player, session);
        syncClientAuthorization(player, false, registered);

        if (registered) {
            openLoginPrompt(player, "", AuthMessageType.NONE);
        } else {
            openRegisterPrompt(player, "", AuthMessageType.NONE);
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        AuthSession session = sessions.remove(player.getUUID());
        releaseProtectedState(player, session);
    }

    public void onPlayerPositionReset(ServerPlayer player) {
        AuthSession session = sessions.get(player.getUUID());
        if (shouldProtectPlayer(session)) {
            moveToHoldingArea(player, session);
        }
    }

    public void onPlayerTick(ServerPlayer player) {
        AuthSession session = sessions.get(player.getUUID());
        if (!shouldProtectPlayer(session)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (shouldUseTimeout(session) && now >= session.getDeadlineAtMillis()) {
            sessions.remove(player.getUUID());
            releaseProtectedState(player, session);
            player.connection.disconnect(Component.literal("Время на вход истекло.").withStyle(ChatFormatting.RED));
            return;
        }

        applyProtectedState(player, session);
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        ServerLevel lockLevel = player.server.getLevel(session.getLockDimension());
        if (lockLevel == null) {
            lockLevel = player.serverLevel();
            session.captureLock(player);
        }

        boolean wrongDimension = !player.serverLevel().dimension().equals(session.getLockDimension());
        double distance = wrongDimension ? Double.MAX_VALUE : player.distanceToSqr(session.getLockX(), session.getLockY(), session.getLockZ());
        float yawDelta = Math.abs(player.getYRot() - session.getLockYaw());
        float pitchDelta = Math.abs(player.getXRot() - session.getLockPitch());
        if (wrongDimension || distance > 0.0001D || yawDelta > 0.01F || pitchDelta > 0.01F) {
            player.teleportTo(lockLevel, session.getLockX(), session.getLockY(), session.getLockZ(), session.getLockYaw(), session.getLockPitch());
        }
    }

    public boolean isAuthorized(ServerPlayer player) {
        AuthSession session = sessions.get(player.getUUID());
        return session == null || session.isAuthorized();
    }

    public boolean shouldBlock(Player player) {
        return player instanceof ServerPlayer serverPlayer && isInteractionRestricted(serverPlayer);
    }

    public int openLoginPromptCommand(ServerPlayer player) {
        if (isAuthorized(player)) {
            player.sendSystemMessage(Component.literal("Вы уже вошли. Для смены пароля используйте /changepassword.").withStyle(ChatFormatting.AQUA));
            return 1;
        }
        if (!playerStore.isRegistered(player.getGameProfile().getName())) {
            openRegisterPrompt(player, "Аккаунт еще не зарегистрирован.", AuthMessageType.INFO);
            return 1;
        }
        openLoginPrompt(player, "", AuthMessageType.NONE);
        return 1;
    }

    public int openRegisterPromptCommand(ServerPlayer player) {
        if (playerStore.isRegistered(player.getGameProfile().getName())) {
            if (isAuthorized(player)) {
                player.sendSystemMessage(Component.literal("Аккаунт уже зарегистрирован. Для смены пароля используйте /changepassword.").withStyle(ChatFormatting.AQUA));
            } else {
                openLoginPrompt(player, "Аккаунт уже зарегистрирован. Введите пароль.", AuthMessageType.INFO);
            }
            return 1;
        }
        openRegisterPrompt(player, "", AuthMessageType.NONE);
        return 1;
    }

    public int openChangePasswordPromptCommand(ServerPlayer player) {
        if (!playerStore.isRegistered(player.getGameProfile().getName())) {
            openRegisterPrompt(player, "Сначала создайте пароль.", AuthMessageType.INFO);
            return 1;
        }
        if (!isAuthorized(player)) {
            openLoginPrompt(player, "Сначала войдите в аккаунт.", AuthMessageType.INFO);
            return 1;
        }
        openChangePasswordPrompt(player, "", AuthMessageType.NONE);
        return 1;
    }

    public int openDefaultPromptCommand(ServerPlayer player) {
        if (!player.server.isDedicatedServer()) {
            player.sendSystemMessage(Component.literal("В одиночном мире авторизация отключена.").withStyle(ChatFormatting.AQUA));
            return 1;
        }
        if (!playerStore.isRegistered(player.getGameProfile().getName())) {
            return openRegisterPromptCommand(player);
        }
        if (!isAuthorized(player)) {
            return openLoginPromptCommand(player);
        }
        return openChangePasswordPromptCommand(player);
    }

    public int chatLoginCommand(ServerPlayer player, String password) {
        if (!player.server.isDedicatedServer()) {
            player.sendSystemMessage(Component.literal("В одиночном мире авторизация отключена.").withStyle(ChatFormatting.AQUA));
            return 1;
        }
        PlayerPasswordStore.StoredPassword storedPassword = playerStore.getStoredPassword(player.getGameProfile().getName());
        AuthSession session = sessions.get(player.getUUID());
        if (storedPassword == null || session == null) {
            openRegisterPrompt(player, "Аккаунт еще не зарегистрирован.", AuthMessageType.INFO);
            return 0;
        }
        if (session.isAuthorized()) {
            player.sendSystemMessage(Component.literal("Вы уже вошли.").withStyle(ChatFormatting.AQUA));
            return 1;
        }
        if (password == null || password.isBlank()) {
            player.sendSystemMessage(Component.literal("Пароль не может быть пустым.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (isPasswordTooLong(password)) {
            player.sendSystemMessage(Component.literal("Пароль не должен превышать 40 символов.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String passwordHash = HashingUtil.derivePasswordHash(storedPassword.hashVersion(), player.getGameProfile().getName(), storedPassword.passwordSalt(), password);
        if (!HashingUtil.constantTimeEquals(storedPassword.passwordHash(), passwordHash)) {
            handleFailedAttempt(player, session, AuthScreenMode.LOGIN, "Неверный пароль.");
            return 0;
        }

        playerStore.recordSuccessfulLogin(player);
        session.setRegistered(true);
        restorePlayerFromHoldingArea(player, session);
        session.authorize();
        syncClientAuthorization(player, true, true);
        syncLocalHash(player, passwordHash);
        return 1;
    }

    public int chatRegisterCommand(ServerPlayer player, String password, String confirmation) {
        if (!player.server.isDedicatedServer()) {
            player.sendSystemMessage(Component.literal("В одиночном мире авторизация отключена.").withStyle(ChatFormatting.AQUA));
            return 1;
        }
        if (playerStore.isRegistered(player.getGameProfile().getName())) {
            player.sendSystemMessage(Component.literal("Аккаунт уже зарегистрирован. Используйте /thebestloginchat login <пароль>.").withStyle(ChatFormatting.AQUA));
            return 0;
        }
        if (password == null || password.isBlank()) {
            player.sendSystemMessage(Component.literal("Пароль не может быть пустым.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (isPasswordTooLong(password) || isPasswordTooLong(confirmation)) {
            player.sendSystemMessage(Component.literal("Пароль не должен превышать 40 символов.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!password.equals(confirmation)) {
            player.sendSystemMessage(Component.literal("Пароли не совпадают.").withStyle(ChatFormatting.RED));
            return 0;
        }

        AuthSession session = getOrCreateSession(player);
        String newSalt = HashingUtil.randomHex(16);
        String passwordHash = HashingUtil.derivePasswordHash(HashingUtil.CURRENT_HASH_VERSION, player.getGameProfile().getName(), newSalt, password);
        playerStore.setPassword(player.getGameProfile().getName(), newSalt, HashingUtil.CURRENT_HASH_VERSION, passwordHash);
        playerStore.recordSuccessfulLogin(player);
        session.setRegistered(true);
        restorePlayerFromHoldingArea(player, session);
        session.authorize();
        syncClientAuthorization(player, true, true);
        syncLocalHash(player, passwordHash);
        return 1;
    }

    public int chatChangePasswordCommand(ServerPlayer player, String oldPassword, String newPassword, String confirmation) {
        if (!player.server.isDedicatedServer()) {
            player.sendSystemMessage(Component.literal("В одиночном мире авторизация отключена.").withStyle(ChatFormatting.AQUA));
            return 1;
        }
        PlayerPasswordStore.StoredPassword storedPassword = playerStore.getStoredPassword(player.getGameProfile().getName());
        AuthSession session = sessions.get(player.getUUID());
        if (storedPassword == null || session == null) {
            player.sendSystemMessage(Component.literal("Сначала зарегистрируйте аккаунт.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!session.isAuthorized()) {
            player.sendSystemMessage(Component.literal("Сначала войдите в аккаунт.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            player.sendSystemMessage(Component.literal("Введите текущий пароль.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (newPassword == null || newPassword.isBlank()) {
            player.sendSystemMessage(Component.literal("Новый пароль не может быть пустым.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (isPasswordTooLong(oldPassword) || isPasswordTooLong(newPassword) || isPasswordTooLong(confirmation)) {
            player.sendSystemMessage(Component.literal("Пароль не должен превышать 40 символов.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!newPassword.equals(confirmation)) {
            player.sendSystemMessage(Component.literal("Подтверждение нового пароля не совпадает.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String oldHash = HashingUtil.derivePasswordHash(storedPassword.hashVersion(), player.getGameProfile().getName(), storedPassword.passwordSalt(), oldPassword);
        if (!HashingUtil.constantTimeEquals(storedPassword.passwordHash(), oldHash)) {
            handleFailedAttempt(player, session, AuthScreenMode.CHANGE_PASSWORD, "Текущий пароль указан неверно.");
            return 0;
        }

        String newSalt = HashingUtil.randomHex(16);
        String newHash = HashingUtil.derivePasswordHash(HashingUtil.CURRENT_HASH_VERSION, player.getGameProfile().getName(), newSalt, newPassword);
        if (HashingUtil.constantTimeEquals(storedPassword.passwordHash(), newHash)) {
            player.sendSystemMessage(Component.literal("Новый пароль совпадает с текущим.").withStyle(ChatFormatting.RED));
            return 0;
        }

        playerStore.setPassword(player.getGameProfile().getName(), newSalt, HashingUtil.CURRENT_HASH_VERSION, newHash);
        playerStore.recordSuccessfulLogin(player);
        syncLocalHash(player, newHash);
        syncClientAuthorization(player, true, true);
        return 1;
    }

    public void handleLoginProof(ServerPlayer player, String challenge, String proof, boolean automatic) {
        PlayerPasswordStore.StoredPassword storedPassword = playerStore.getStoredPassword(player.getGameProfile().getName());
        AuthSession session = sessions.get(player.getUUID());
        if (session == null || session.isAuthorized()) {
            return;
        }
        if (storedPassword == null) {
            openRegisterPrompt(player, "Аккаунт еще не зарегистрирован.", AuthMessageType.INFO);
            return;
        }
        if (!HashingUtil.isSha256Hex(challenge) || !HashingUtil.isSha256Hex(proof)) {
            if (!automatic) {
                openLoginPrompt(player, "Окно входа устарело. Откройте его снова.", AuthMessageType.ERROR);
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (!session.consumeChallenge(challenge, now, CHALLENGE_TTL_MILLIS)) {
            openLoginPrompt(player, "Окно входа устарело. Откройте его снова.", automatic ? AuthMessageType.INFO : AuthMessageType.ERROR);
            return;
        }

        String storedClientHash = HashingUtil.deriveStoredClientHash(configStore.getServerId(), storedPassword.passwordHash());
        String expectedProof = HashingUtil.deriveProof(configStore.getServerId(), player.getGameProfile().getName(), storedClientHash, challenge);
        if (!HashingUtil.constantTimeEquals(expectedProof, proof)) {
            if (automatic) {
                clearLocalHash(player);
                openLoginPrompt(player, "", AuthMessageType.NONE);
                return;
            }
            handleFailedAttempt(player, session, AuthScreenMode.LOGIN, "Неверный пароль.");
            return;
        }

        playerStore.recordSuccessfulLogin(player);
        session.setRegistered(true);
        restorePlayerFromHoldingArea(player, session);
        session.authorize();
        syncClientAuthorization(player, true, true);
        syncLocalHash(player, storedPassword.passwordHash());
    }

    public void handleProof(ServerPlayer player, String challenge, String proof) {
        handleLoginProof(player, challenge, proof, true);
    }

    public void handleRegistration(ServerPlayer player, String challenge, String encryptedPasswordHash) {
        AuthSession session = sessions.get(player.getUUID());
        if (session == null || session.isAuthorized()) {
            return;
        }
        if (playerStore.isRegistered(player.getGameProfile().getName())) {
            session.setRegistered(true);
            openLoginPrompt(player, "Аккаунт уже зарегистрирован. Введите пароль.", AuthMessageType.INFO);
            return;
        }
        if (!HashingUtil.isSha256Hex(challenge)) {
            openRegisterPrompt(player, "Окно регистрации устарело. Откройте его снова.", AuthMessageType.ERROR);
            return;
        }

        long now = System.currentTimeMillis();
        if (!session.consumeChallenge(challenge, now, CHALLENGE_TTL_MILLIS)) {
            openRegisterPrompt(player, "Окно регистрации устарело. Откройте его снова.", AuthMessageType.ERROR);
            return;
        }

        String passwordHash = decryptPasswordHash(encryptedPasswordHash);
        if (!HashingUtil.isSha256Hex(passwordHash)) {
            openRegisterPrompt(player, "Не удалось обработать пароль. Попробуйте еще раз.", AuthMessageType.ERROR);
            return;
        }

        String emptyPasswordHash = HashingUtil.derivePasswordHash(session.getTargetHashVersion(), player.getGameProfile().getName(), session.getTargetSalt(), "");
        if (HashingUtil.constantTimeEquals(emptyPasswordHash, passwordHash)) {
            openRegisterPrompt(player, "Пароль не может быть пустым.", AuthMessageType.ERROR);
            return;
        }

        playerStore.setPassword(player.getGameProfile().getName(), session.getTargetSalt(), session.getTargetHashVersion(), passwordHash);
        playerStore.recordSuccessfulLogin(player);
        session.setRegistered(true);
        restorePlayerFromHoldingArea(player, session);
        session.authorize();
        syncClientAuthorization(player, true, true);
        syncLocalHash(player, passwordHash);
    }

    public void handleChangePassword(ServerPlayer player, String challenge, String oldProof, String encryptedPasswordHash) {
        PlayerPasswordStore.StoredPassword storedPassword = playerStore.getStoredPassword(player.getGameProfile().getName());
        AuthSession session = sessions.get(player.getUUID());
        if (storedPassword == null || session == null) {
            return;
        }
        if (!session.isAuthorized()) {
            openLoginPrompt(player, "Сначала войдите в аккаунт.", AuthMessageType.INFO);
            return;
        }
        if (!HashingUtil.isSha256Hex(challenge) || !HashingUtil.isSha256Hex(oldProof)) {
            openChangePasswordPrompt(player, "Окно смены пароля устарело. Откройте его снова.", AuthMessageType.ERROR);
            return;
        }

        long now = System.currentTimeMillis();
        if (!session.consumeChallenge(challenge, now, Long.MAX_VALUE)) {
            openChangePasswordPrompt(player, "Окно смены пароля устарело. Откройте его снова.", AuthMessageType.ERROR);
            return;
        }

        String storedClientHash = HashingUtil.deriveStoredClientHash(configStore.getServerId(), storedPassword.passwordHash());
        String expectedOldProof = HashingUtil.deriveProof(configStore.getServerId(), player.getGameProfile().getName(), storedClientHash, challenge);
        if (!HashingUtil.constantTimeEquals(expectedOldProof, oldProof)) {
            handleFailedAttempt(player, session, AuthScreenMode.CHANGE_PASSWORD, "Текущий пароль указан неверно.");
            return;
        }

        String newPasswordHash = decryptPasswordHash(encryptedPasswordHash);
        if (!HashingUtil.isSha256Hex(newPasswordHash)) {
            openChangePasswordPrompt(player, "Не удалось обработать новый пароль. Попробуйте еще раз.", AuthMessageType.ERROR);
            return;
        }

        String emptyPasswordHash = HashingUtil.derivePasswordHash(session.getTargetHashVersion(), player.getGameProfile().getName(), session.getTargetSalt(), "");
        if (HashingUtil.constantTimeEquals(emptyPasswordHash, newPasswordHash)) {
            openChangePasswordPrompt(player, "Новый пароль не может быть пустым.", AuthMessageType.ERROR);
            return;
        }
        if (HashingUtil.constantTimeEquals(storedPassword.passwordHash(), newPasswordHash)) {
            openChangePasswordPrompt(player, "Новый пароль совпадает с текущим.", AuthMessageType.ERROR);
            return;
        }

        playerStore.setPassword(player.getGameProfile().getName(), session.getTargetSalt(), session.getTargetHashVersion(), newPasswordHash);
        playerStore.recordSuccessfulLogin(player);
        restorePlayerFromHoldingArea(player, session);
        session.authorize();
        syncLocalHash(player, newPasswordHash);
        syncClientAuthorization(player, true, true);
    }

    public void cancelActivePrompt(ServerPlayer player) {
        AuthSession session = sessions.get(player.getUUID());
        if (session == null || !session.isAuthorized() || session.getPromptMode() != AuthScreenMode.CHANGE_PASSWORD || !session.hasActivePrompt()) {
            return;
        }
        restorePlayerFromHoldingArea(player, session);
        session.clearPrompt();
        syncClientAuthorization(player, true, true);
    }

    public int unregister(CommandSourceStack source, String nickname) {
        if (!playerStore.remove(nickname)) {
            source.sendFailure(Component.literal("Игрок не зарегистрирован.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (source.getServer() != null) {
            ServerPlayer online = findOnlinePlayer(source.getServer(), nickname);
            if (online != null) {
                AuthSession session = getOrCreateSession(online);
                session.restart(false, nextDeadline(), online);
                moveToHoldingArea(online, session);
                clearLocalHash(online);
                syncClientAuthorization(online, false, false);
                online.closeContainer();
                openRegisterPrompt(online, "Регистрация была удалена администратором.", AuthMessageType.INFO);
            }
        }

        source.sendSuccess(() -> Component.literal("Регистрация игрока удалена.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public int adminChangePassword(CommandSourceStack source, String nickname, String password) {
        if (password.isBlank()) {
            source.sendFailure(Component.literal("Пароль не может быть пустым.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (isPasswordTooLong(password)) {
            source.sendFailure(Component.literal("Пароль не должен превышать 40 символов.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!playerStore.isRegistered(nickname)) {
            source.sendFailure(Component.literal("Игрок не зарегистрирован.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String newSalt = HashingUtil.randomHex(16);
        String newPasswordHash = HashingUtil.derivePasswordHash(HashingUtil.CURRENT_HASH_VERSION, nickname, newSalt, password);
        playerStore.setPassword(nickname, newSalt, HashingUtil.CURRENT_HASH_VERSION, newPasswordHash);
        if (source.getServer() != null) {
            ServerPlayer online = findOnlinePlayer(source.getServer(), nickname);
            if (online != null) {
                AuthSession session = getOrCreateSession(online);
                session.restart(true, nextDeadline(), online);
                moveToHoldingArea(online, session);
                clearLocalHash(online);
                syncClientAuthorization(online, false, true);
                online.closeContainer();
                openLoginPrompt(online, "Пароль был изменен администратором. Введите новый пароль.", AuthMessageType.INFO);
            }
        }
        source.sendSuccess(() -> Component.literal("Пароль для игрока " + nickname + " обновлен.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public int reload(CommandSourceStack source) {
        try {
            configStore.loadOrCreate();
            playerStore.loadOrCreate();
            source.sendSuccess(() -> Component.literal("The Best Login перезагружен.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalStateException exception) {
            LOGGER.error("Failed to reload The Best Login", exception);
            source.sendFailure(Component.literal("Не удалось перезагрузить мод. Подробности смотрите в логе сервера.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    public List<String> getRegisteredNicknames() {
        return playerStore.getRegisteredNicknames();
    }

    private void openLoginPrompt(ServerPlayer player, String message, AuthMessageType type) {
        PlayerPasswordStore.StoredPassword storedPassword = playerStore.getStoredPassword(player.getGameProfile().getName());
        if (storedPassword == null) {
            openRegisterPrompt(player, message.isBlank() ? "Аккаунт еще не зарегистрирован." : message, message.isBlank() ? AuthMessageType.INFO : type);
            return;
        }

        AuthSession session = getOrCreateSession(player);
        session.setRegistered(true);
        session.preparePrompt(
                AuthScreenMode.LOGIN,
                storedPassword.hashVersion(),
                storedPassword.passwordSalt(),
                storedPassword.hashVersion(),
                storedPassword.passwordSalt(),
                HashingUtil.randomHex(32),
                System.currentTimeMillis()
        );
        sendPrompt(player, session, message, type);
    }

    private void openRegisterPrompt(ServerPlayer player, String message, AuthMessageType type) {
        if (playerStore.isRegistered(player.getGameProfile().getName())) {
            openLoginPrompt(player, message.isBlank() ? "Аккаунт уже зарегистрирован. Введите пароль." : message, message.isBlank() ? AuthMessageType.INFO : type);
            return;
        }

        AuthSession session = getOrCreateSession(player);
        session.setRegistered(false);
        String registerSalt = HashingUtil.randomHex(16);
        session.preparePrompt(
                AuthScreenMode.REGISTER,
                HashingUtil.CURRENT_HASH_VERSION,
                registerSalt,
                HashingUtil.CURRENT_HASH_VERSION,
                registerSalt,
                HashingUtil.randomHex(32),
                System.currentTimeMillis()
        );
        sendPrompt(player, session, message, type);
    }

    private void openChangePasswordPrompt(ServerPlayer player, String message, AuthMessageType type) {
        PlayerPasswordStore.StoredPassword storedPassword = playerStore.getStoredPassword(player.getGameProfile().getName());
        if (storedPassword == null) {
            openRegisterPrompt(player, message.isBlank() ? "Сначала создайте пароль." : message, message.isBlank() ? AuthMessageType.INFO : type);
            return;
        }

        AuthSession session = getOrCreateSession(player);
        session.preparePrompt(
                AuthScreenMode.CHANGE_PASSWORD,
                storedPassword.hashVersion(),
                storedPassword.passwordSalt(),
                HashingUtil.CURRENT_HASH_VERSION,
                HashingUtil.randomHex(16),
                HashingUtil.randomHex(32),
                System.currentTimeMillis()
        );
        moveToHoldingArea(player, session);
        sendPrompt(player, session, message, type);
    }

    private void sendPrompt(ServerPlayer player, AuthSession session, String message, AuthMessageType type) {
        if (!TheBestLoginNetwork.canSendTo(player)) {
            return;
        }
        TheBestLoginNetwork.sendToPlayer(player, new AuthPromptS2CPacket(
                session.getPromptMode(),
                player.getGameProfile().getName(),
                configStore.getServerId(),
                session.getChallenge(),
                session.getCurrentHashVersion(),
                session.getCurrentSalt(),
                session.getTargetHashVersion(),
                session.getTargetSalt(),
                transportPublicKey,
                message == null ? "" : message,
                type == null ? AuthMessageType.NONE : type,
                secondsRemaining(session)
        ));
    }

    private void handleFailedAttempt(ServerPlayer player, AuthSession session, AuthScreenMode mode, String baseMessage) {
        int usedAttempts = session.incrementFailedAttempts();
        int remainingAttempts = Math.max(0, MAX_LOGIN_ATTEMPTS - usedAttempts);
        if (remainingAttempts <= 0) {
            sessions.remove(player.getUUID());
            clearLocalHash(player);
            releaseProtectedState(player, session);
            player.connection.disconnect(Component.literal("Слишком много неверных попыток. Подключитесь снова.").withStyle(ChatFormatting.RED));
            return;
        }

        String fullMessage = baseMessage + " Осталось попыток: " + remainingAttempts + '.';
        if (mode == AuthScreenMode.CHANGE_PASSWORD) {
            openChangePasswordPrompt(player, fullMessage, AuthMessageType.ERROR);
        } else {
            openLoginPrompt(player, fullMessage, AuthMessageType.ERROR);
        }
    }

    private void moveToHoldingArea(ServerPlayer player, AuthSession session) {
        if (!session.isHoldingAreaActive()) {
            session.captureReturnPosition(player);
            session.captureOriginalAbilities(player);
        }
        session.captureLock(player);
        session.setHoldingAreaActive(true);
        applyProtectedState(player, session);
    }

    private void restorePlayerFromHoldingArea(ServerPlayer player, AuthSession session) {
        releaseProtectedState(player, session);
        if (!session.isHoldingAreaActive()) {
            return;
        }

        ServerLevel returnLevel = session.getReturnDimension() == null ? null : player.server.getLevel(session.getReturnDimension());
        double returnX = session.getReturnX();
        double returnY = session.getReturnY();
        double returnZ = session.getReturnZ();
        float returnYaw = session.getReturnYaw();
        float returnPitch = session.getReturnPitch();

        if (returnLevel != null) {
            player.teleportTo(returnLevel, returnX, returnY, returnZ, returnYaw, returnPitch);
        }
        session.setHoldingAreaActive(false);
    }

    private void applyProtectedState(ServerPlayer player, AuthSession session) {
        if (session == null) {
            return;
        }
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        boolean needsAbilityUpdate = false;
        if (!player.getAbilities().mayfly || player.getAbilities().flying) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = false;
            needsAbilityUpdate = true;
        }
        if (!player.getAbilities().invulnerable) {
            player.getAbilities().invulnerable = true;
            needsAbilityUpdate = true;
        }
        if (needsAbilityUpdate) {
            player.onUpdateAbilities();
        }
    }

    private void releaseProtectedState(ServerPlayer player, AuthSession session) {
        restoreFlightState(player, session);
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
    }

    private void restoreFlightState(ServerPlayer player, AuthSession session) {
        if (session == null) {
            return;
        }
        boolean targetMayfly = session.isOriginalMayfly();
        boolean targetFlying = targetMayfly && session.isOriginalFlying();
        boolean targetInvulnerable = session.isOriginalInvulnerable();
        if (player.getAbilities().mayfly != targetMayfly
                || player.getAbilities().flying != targetFlying
                || player.getAbilities().invulnerable != targetInvulnerable) {
            player.getAbilities().mayfly = targetMayfly;
            player.getAbilities().flying = targetFlying;
            player.getAbilities().invulnerable = targetInvulnerable;
            player.onUpdateAbilities();
        }
    }

    private void syncLocalHash(ServerPlayer player, String passwordHash) {
        if (!TheBestLoginNetwork.canSendTo(player)) {
            return;
        }

        String storedClientHash = HashingUtil.deriveStoredClientHash(configStore.getServerId(), passwordHash);
        TheBestLoginNetwork.sendToPlayer(player, new LocalPasswordSyncS2CPacket(player.getGameProfile().getName(), configStore.getServerId(), storedClientHash));
    }

    private void clearLocalHash(ServerPlayer player) {
        if (!TheBestLoginNetwork.canSendTo(player)) {
            return;
        }
        TheBestLoginNetwork.sendToPlayer(player, new LocalPasswordSyncS2CPacket(player.getGameProfile().getName(), configStore.getServerId(), ""));
    }

    private void syncClientAuthorization(ServerPlayer player, boolean authenticated, boolean registered) {
        if (!TheBestLoginNetwork.canSendTo(player)) {
            return;
        }
        TheBestLoginNetwork.sendToPlayer(player, new AuthStateS2CPacket(authenticated, registered));
    }

    private boolean isPasswordTooLong(String password) {
        return password != null && password.length() > MAX_PASSWORD_LENGTH;
    }

    private String decryptPasswordHash(String encryptedPasswordHash) {
        if (encryptedPasswordHash == null || encryptedPasswordHash.isBlank() || transportKeyPair == null) {
            return "";
        }
        try {
            return HashingUtil.decryptFromClient(transportKeyPair.getPrivate(), encryptedPasswordHash).trim();
        } catch (IllegalStateException exception) {
            LOGGER.warn("Failed to decrypt The Best Login payload", exception);
            return "";
        }
    }

    private AuthSession getOrCreateSession(ServerPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), ignored -> new AuthSession(playerStore.isRegistered(player.getGameProfile().getName()), nextDeadline(), player));
    }

    private int secondsRemaining(AuthSession session) {
        if (session.isAuthorized() && session.getPromptMode() == AuthScreenMode.CHANGE_PASSWORD) {
            return 0;
        }
        long remaining = Math.max(0L, session.getDeadlineAtMillis() - System.currentTimeMillis());
        return (int) ((remaining + 999L) / 1000L);
    }

    private long nextDeadline() {
        return System.currentTimeMillis() + (long) configStore.getLoginTimeoutSeconds() * 1000L;
    }

    private ServerPlayer findOnlinePlayer(MinecraftServer server, String nickname) {
        String normalized = HashingUtil.normalizeNickname(nickname);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (HashingUtil.normalizeNickname(player.getGameProfile().getName()).equals(normalized)) {
                return player;
            }
        }
        return null;
    }

    private boolean isInteractionRestricted(ServerPlayer player) {
        return shouldProtectPlayer(sessions.get(player.getUUID()));
    }

    private boolean shouldProtectPlayer(AuthSession session) {
        return session != null
                && session.hasActivePrompt()
                && (!session.isAuthorized() || session.getPromptMode() == AuthScreenMode.CHANGE_PASSWORD);
    }

    private boolean shouldUseTimeout(AuthSession session) {
        return session != null && !session.isAuthorized();
    }
}
