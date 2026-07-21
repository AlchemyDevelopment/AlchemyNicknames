package com.github.plunk.alchemypersona.joinmessages;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public class Data {
    private static File file;
    private static FileConfiguration data;

    public static void setup(Plugin plugin) {
        file = new File(plugin.getDataFolder(), "join_messages_data.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get() {
        return data;
    }

    public static void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void reload() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    public static void setInMemory(java.util.UUID uuid, String joinMessageId) {
        if (data != null) {
            if (joinMessageId == null || joinMessageId.isEmpty()) {
                data.set("players." + uuid.toString(), null);
            } else {
                data.set("players." + uuid.toString(), joinMessageId);
            }
        }
    }
}


