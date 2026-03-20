package com.thebestlogin.util;

import net.minecraftforge.fml.loading.FMLPaths;
import com.thebestlogin.TheBestLoginMod;

import java.nio.file.Path;

public final class TheBestLoginPaths {
    private TheBestLoginPaths() {
    }

    public static Path baseDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(TheBestLoginMod.MOD_ID);
    }

    public static Path serverConfigFile() {
        return baseDirectory().resolve("config.json");
    }

    public static Path playersFile() {
        return baseDirectory().resolve("playerspw.json");
    }

    public static Path clientPasswordsFile() {
        return baseDirectory().resolve("passwords.json");
    }
}
