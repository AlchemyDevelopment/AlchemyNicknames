package com.github.plunk.alchemypersona.nicknames.managers;

import com.github.plunk.alchemypersona.AlchemyPersona;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NicknameManager {

    private final AlchemyPersona plugin;
    private final Map<UUID, String> nicknames = new java.util.concurrent.ConcurrentHashMap<>();
    private final File nicknamesFile;
    private FileConfiguration nicknamesConfig;
    private FileConfiguration settings;

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public NicknameManager(AlchemyPersona plugin) {
        this.plugin = plugin;
        this.nicknamesFile = new File(plugin.getDataFolder(), "nicknames_data.yml");
        loadSettings();
    }

    public void loadSettings() {
        this.settings = plugin.getNicknamesConfig();
    }

    public void loadNicknames() {
        if (!nicknamesFile.exists()) {
            try { nicknamesFile.createNewFile(); } catch (IOException ignored) {}
        }
        nicknamesConfig = YamlConfiguration.loadConfiguration(nicknamesFile);
        nicknames.clear();
        for (String key : nicknamesConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String nick = nicknamesConfig.getString(key);
                nicknames.put(uuid, nick);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveNicknames() {
        if (nicknamesConfig == null) {
            nicknamesConfig = new YamlConfiguration();
        }
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            nicknamesConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            nicknamesConfig.save(nicknamesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save nicknames_data.yml!");
        }
    }

    public void setNicknameInMemory(UUID uuid, String nick) {
        if (nick == null || nick.isEmpty()) {
            nicknames.remove(uuid);
        } else {
            nicknames.put(uuid, nick);
        }
        updatePlayer(uuid);
    }

    public void setNickname(UUID uuid, String nick) {
        if (plugin.getConfig().getBoolean("velocity-sync.enabled", false)) {
            setNicknameInMemory(uuid, nick);
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getMessagingHandler().updatePersona(player, nick, "", plugin.getTagManager().getPlayerTagId(uuid), plugin.getJoinMessageSelection(uuid));
            }
            return;
        }

        if (nick == null || nick.isEmpty()) {
            nicknames.remove(uuid);
            nicknamesConfig.set(uuid.toString(), null);
        } else {
            nicknames.put(uuid, nick);
        }
        saveNicknames();
        updatePlayer(uuid);
    }

    public String getNickname(UUID uuid) {
        return nicknames.get(uuid);
    }

    public boolean hasNickname(UUID uuid) {
        return nicknames.containsKey(uuid);
    }

    /**
     * Parse a nickname into a Component, supporting MiniMessage, Hex, and Legacy.
     */
    public Component parseNickname(String nick) {
        if (nick == null) return null;

        // 1. Convert Legacy Hex (&#RRGGBB) to MiniMessage (<#RRGGBB>)
        String processed = translateHexCodes(nick);

        // 2. Wrap legacy colors (&c) in MiniMessage if needed, 
        // but it's easier to just deserialize legacy then serialize to MiniMessage or vice versa.
        // Modern approach: Paper's LegacyComponentSerializer can handle & colors.
        
        // If it looks like MiniMessage (contains <), use MiniMessage
        if (processed.contains("<")) {
             // Frontend emits <underline> but MiniMessage requires <underlined>
             processed = processed.replace("<underline>", "<underlined>").replace("</underline>", "</underlined>");
             return MiniMessage.miniMessage().deserialize(processed);
        } else {
             // Otherwise treat as legacy. Handle both & and §
             processed = processed.replace("§", "&");
             return LegacyComponentSerializer.legacyAmpersand().deserialize(processed);
        }
    }

    public Component getDisplayNickname(Player player) {
        String nick = nicknames.get(player.getUniqueId());
        if (nick == null) {
            return Component.text(player.getName());
        }
        return parseNickname(nick);
    }

    private String translateHexCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void updatePlayer(UUID uuid) {
        // We no longer update the player's display name directly to avoid conflicts.
        // The nickname is served via PlaceholderAPI.
    }
}
