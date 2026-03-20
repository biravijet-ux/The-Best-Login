package com.thebestlogin.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import com.thebestlogin.util.TheBestLoginPaths;
import com.thebestlogin.util.HashingUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerPasswordStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, PlayerPasswordRecord>>() {}.getType();

    private final Map<String, PlayerPasswordRecord> records = new LinkedHashMap<>();

    public synchronized void loadOrCreate() {
        Path file = TheBestLoginPaths.playersFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                records.clear();
                saveInternal(file);
                return;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, PlayerPasswordRecord> loaded = GSON.fromJson(reader, STORE_TYPE);
                records.clear();
                if (loaded != null) {
                    for (Map.Entry<String, PlayerPasswordRecord> entry : loaded.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) {
                            continue;
                        }
                        PlayerPasswordRecord record = entry.getValue();
                        if (record.passwordHash == null || record.passwordHash.isBlank()) {
                            continue;
                        }
                        if (record.hashVersion <= 0) {
                            record.hashVersion = record.passwordSalt == null || record.passwordSalt.isBlank()
                                    ? HashingUtil.LEGACY_HASH_VERSION
                                    : HashingUtil.CURRENT_HASH_VERSION;
                        }
                        if (record.hashVersion == HashingUtil.LEGACY_HASH_VERSION) {
                            record.passwordSalt = "";
                        } else if (record.passwordSalt == null) {
                            record.passwordSalt = HashingUtil.randomHex(16);
                        }
                        if (record.displayNickname == null || record.displayNickname.isBlank()) {
                            record.displayNickname = entry.getKey();
                        }
                        records.put(HashingUtil.normalizeNickname(entry.getKey()), record);
                    }
                }
            }
            saveInternal(file);
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Failed to load player password store", exception);
        }
    }

    public synchronized boolean isRegistered(String nickname) {
        return records.containsKey(HashingUtil.normalizeNickname(nickname));
    }

    public synchronized StoredPassword getStoredPassword(String nickname) {
        PlayerPasswordRecord record = records.get(HashingUtil.normalizeNickname(nickname));
        if (record == null) {
            return null;
        }
        return new StoredPassword(record.passwordHash, record.passwordSalt == null ? "" : record.passwordSalt, record.hashVersion);
    }

    public synchronized void setPassword(String nickname, String passwordSalt, int hashVersion, String passwordHash) {
        String key = HashingUtil.normalizeNickname(nickname);
        PlayerPasswordRecord record = records.computeIfAbsent(key, ignored -> new PlayerPasswordRecord());
        record.displayNickname = nickname;
        record.passwordHash = passwordHash;
        record.passwordSalt = hashVersion == HashingUtil.LEGACY_HASH_VERSION ? "" : passwordSalt;
        record.hashVersion = hashVersion;
        saveOrThrow();
    }

    public synchronized boolean remove(String nickname) {
        PlayerPasswordRecord removed = records.remove(HashingUtil.normalizeNickname(nickname));
        if (removed != null) {
            saveOrThrow();
            return true;
        }
        return false;
    }

    public synchronized void recordSuccessfulLogin(ServerPlayer player) {
        String key = HashingUtil.normalizeNickname(player.getGameProfile().getName());
        PlayerPasswordRecord record = records.get(key);
        if (record == null) {
            return;
        }
        record.displayNickname = player.getGameProfile().getName();
        record.lastLoginAt = Instant.now().toString();
        record.lastLoginIp = player.getIpAddress();
        saveOrThrow();
    }

    public synchronized List<String> getRegisteredNicknames() {
        ArrayList<String> nicknames = new ArrayList<>(records.size());
        for (Map.Entry<String, PlayerPasswordRecord> entry : records.entrySet()) {
            String nickname = entry.getValue().displayNickname;
            if (nickname == null || nickname.isBlank()) {
                nickname = entry.getKey();
            }
            nicknames.add(nickname);
        }
        nicknames.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(nicknames);
    }

    private void saveOrThrow() {
        try {
            saveInternal(TheBestLoginPaths.playersFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save player password store", exception);
        }
    }

    private void saveInternal(Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(records, STORE_TYPE, writer);
        }
    }

    public record StoredPassword(String passwordHash, String passwordSalt, int hashVersion) {
    }

    private static final class PlayerPasswordRecord {
        private String displayNickname;
        private String passwordHash;
        private String passwordSalt;
        private int hashVersion;
        private String lastLoginAt;
        private String lastLoginIp;
    }
}
