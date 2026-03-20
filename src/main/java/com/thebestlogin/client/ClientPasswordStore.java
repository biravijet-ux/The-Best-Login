package com.thebestlogin.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.thebestlogin.util.TheBestLoginPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientPasswordStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
    private static final ClientPasswordStore INSTANCE = new ClientPasswordStore();

    private final Map<String, Map<String, String>> data = new LinkedHashMap<>();
    private boolean loaded;

    private ClientPasswordStore() {
    }

    public static ClientPasswordStore get() {
        return INSTANCE;
    }

    public synchronized String getStoredHash(String nickname, String serverId) {
        ensureLoaded();
        Map<String, String> byServer = findByNickname(nickname);
        return byServer == null ? null : byServer.get(serverId);
    }

    public synchronized void putStoredHash(String nickname, String serverId, String storedHash) {
        ensureLoaded();
        if (storedHash == null || storedHash.isBlank()) {
            removeStoredHash(nickname, serverId);
            return;
        }

        String existingKey = findNicknameKey(nickname);
        Map<String, String> byServer;
        if (existingKey == null) {
            byServer = new LinkedHashMap<>();
        } else {
            byServer = data.remove(existingKey);
        }
        byServer.put(serverId, storedHash);
        data.put(nickname, byServer);
        saveOrThrow();
    }

    public synchronized void removeStoredHash(String nickname, String serverId) {
        ensureLoaded();
        String existingKey = findNicknameKey(nickname);
        if (existingKey == null) {
            return;
        }
        Map<String, String> byServer = data.get(existingKey);
        if (byServer == null) {
            return;
        }
        byServer.remove(serverId);
        if (byServer.isEmpty()) {
            data.remove(existingKey);
        }
        saveOrThrow();
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private void load() {
        Path file = TheBestLoginPaths.clientPasswordsFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                data.clear();
                saveInternal(file);
                return;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Map<String, String>> loadedData = GSON.fromJson(reader, STORE_TYPE);
                data.clear();
                if (loadedData != null) {
                    for (Map.Entry<String, Map<String, String>> entry : loadedData.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) {
                            continue;
                        }
                        data.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
                    }
                }
            }
            saveInternal(file);
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Failed to load client password store", exception);
        }
    }

    private void saveOrThrow() {
        try {
            saveInternal(TheBestLoginPaths.clientPasswordsFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save client password store", exception);
        }
    }

    private void saveInternal(Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(data, STORE_TYPE, writer);
        }
    }

    private Map<String, String> findByNickname(String nickname) {
        String key = findNicknameKey(nickname);
        return key == null ? null : data.get(key);
    }

    private String findNicknameKey(String nickname) {
        for (String existing : data.keySet()) {
            if (existing.equalsIgnoreCase(nickname)) {
                return existing;
            }
        }
        return null;
    }
}