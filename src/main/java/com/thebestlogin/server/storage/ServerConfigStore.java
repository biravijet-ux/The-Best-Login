package com.thebestlogin.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thebestlogin.util.TheBestLoginPaths;
import com.thebestlogin.util.HashingUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ServerConfigData data = createDefault();

    public synchronized void loadOrCreate() {
        Path file = TheBestLoginPaths.serverConfigFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                data = createDefault();
                saveInternal(file);
                return;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ServerConfigData loaded = GSON.fromJson(reader, ServerConfigData.class);
                if (loaded == null) {
                    loaded = createDefault();
                }
                if (loaded.serverId == null || loaded.serverId.isBlank()) {
                    loaded.serverId = HashingUtil.randomHex(32);
                }
                if (loaded.loginTimeoutSeconds <= 0) {
                    loaded.loginTimeoutSeconds = 300;
                }
                data = loaded;
            }
            saveInternal(file);
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Failed to load server config", exception);
        }
    }

    public synchronized String getServerId() {
        return data.serverId;
    }

    public synchronized int getLoginTimeoutSeconds() {
        return Math.max(1, data.loginTimeoutSeconds);
    }

    private void saveInternal(Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
    }

    private static ServerConfigData createDefault() {
        ServerConfigData configData = new ServerConfigData();
        configData.serverId = HashingUtil.randomHex(32);
        configData.loginTimeoutSeconds = 300;
        return configData;
    }

    private static final class ServerConfigData {
        private String serverId;
        private int loginTimeoutSeconds;
    }
}
