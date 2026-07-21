package com.github.plunk.alchemypersona.tags.managers;

import com.github.plunk.alchemypersona.AlchemyPersona;
import com.github.plunk.alchemypersona.tags.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TagManager {

    private final AlchemyPersona plugin;
    private final Map<UUID, String> playerTags = new HashMap<>();
    private final File playerDataFile;
    private FileConfiguration playerDataConfig;
    private FileConfiguration tagsConfig;

    public TagManager(AlchemyPersona plugin) {
        this.plugin = plugin;
        this.playerDataFile = new File(plugin.getDataFolder(), "player_tags.yml");
        loadPlayerData();
        loadTagsConfig();
    }

    private void loadTagsConfig() {
        File tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        if (!tagsFile.exists()) {
            plugin.saveResource("tags.yml", false);
        }
        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);
    }

    public FileConfiguration getTagsConfig() {
        return tagsConfig;
    }

    private void loadPlayerData() {
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player_tags.yml!");
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void savePlayerData() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player_tags.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Sets a player's active tag.
     * Stores in memory AND persists to file.
     */
    public void setTagInMemory(UUID uuid, String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            playerTags.remove(uuid);
        } else {
            playerTags.put(uuid, tagId);
        }
    }

    /**
     * Sets a player's active tag.
     * Stores in memory AND persists to file.
     */
    public void setTag(Player player, String tagId) {
        UUID uuid = player.getUniqueId();

        if (plugin.getConfig().getBoolean("velocity-sync.enabled", false)) {
            setTagInMemory(uuid, tagId);
            plugin.getMessagingHandler().updatePersona(player, plugin.getNicknameManager().getNickname(uuid), "", tagId, plugin.getJoinMessageSelection(uuid));
            return;
        }

        // Update memory
        playerTags.put(uuid, tagId);

        // Persist to file
        playerDataConfig.set(uuid.toString(), tagId);
        savePlayerData();
    }

    /**
     * Clears a player's active tag.
     */
    public void clearTag(Player player) {
        UUID uuid = player.getUniqueId();

        if (plugin.getConfig().getBoolean("velocity-sync.enabled", false)) {
            setTagInMemory(uuid, null);
            plugin.getMessagingHandler().updatePersona(player, plugin.getNicknameManager().getNickname(uuid), "", "", plugin.getJoinMessageSelection(uuid));
            return;
        }

        // Update memory
        playerTags.remove(uuid);

        // Persist to file
        playerDataConfig.set(uuid.toString(), null);
        savePlayerData();
    }

    /**
     * Gets the player's current tag ID.
     * First checks memory cache, then falls back to file.
     */
    public String getPlayerTagId(UUID uuid) {
        // Check memory first
        if (playerTags.containsKey(uuid)) {
            return playerTags.get(uuid);
        }

        // Fall back to file (for players who haven't been loaded yet)
        String tagId = playerDataConfig.getString(uuid.toString());
        if (tagId != null) {
            playerTags.put(uuid, tagId); // Cache it
        }
        return tagId;
    }

    /**
     * Loads a player's tag from file into memory (called on join).
     */
    public void loadPlayerTag(UUID uuid) {
        String tagId = playerDataConfig.getString(uuid.toString());
        if (tagId != null) {
            playerTags.put(uuid, tagId);
        }
    }

    /**
     * Gets the display string for a player's tag.
     */
    public String getPlayerTagDisplay(UUID uuid) {
        String tagId = getPlayerTagId(uuid);

        if (tagId == null || tagId.isEmpty()) {
            return "";
        }

        ConfigurationSection section = tagsConfig.getConfigurationSection("tags." + tagId);

        if (section == null) {
            return ""; // Invalid tag ID
        }

        String suffix = "";

        // Nexo check
        if (section.contains("nexo_glyph_id") && Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            try {
                com.nexomc.nexo.glyphs.Glyph glyph = com.nexomc.nexo.NexoPlugin.instance().fontManager()
                        .glyphFromID(section.getString("nexo_glyph_id"));
                if (glyph != null && !glyph.getUnicodes().isEmpty())
                    suffix = glyph.getUnicodes().get(0);
            } catch (Exception ignored) {
            }
        }

        if (suffix.isEmpty())
            suffix = section.getString("tag", section.getString("tag_unicode", ""));

        return ColorUtils.color(suffix);
    }

    /**
     * Grants a tag permission to a player using LuckPerms.
     */
    public CompletableFuture<Boolean> grantTagPermission(Player player, String tagId) {
        ConfigurationSection section = tagsConfig.getConfigurationSection("tags." + tagId);
        if (section == null) {
            return CompletableFuture.completedFuture(false);
        }

        String permission = section.getString("permission");
        if (permission == null || permission.isEmpty()) {
            permission = tagsConfig.getString("default_permission", "deluxetags.tag.%name%")
                    .replace("%name%", tagId);
        }

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return CompletableFuture.completedFuture(false);
        }

        return LuckPermsHook.grantTagPermission(plugin, player, permission);
    }

    /**
     * Reloads the tags configuration.
     */
    public void reload() {
        loadPlayerData();
        loadTagsConfig();
        playerTags.clear(); // Clear cache, will reload on demand
    }
}
