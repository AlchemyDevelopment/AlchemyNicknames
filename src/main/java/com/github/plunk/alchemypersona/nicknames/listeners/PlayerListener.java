package com.github.plunk.alchemypersona.nicknames.listeners;

import com.github.plunk.alchemypersona.AlchemyPersona;
import com.github.plunk.alchemypersona.nicknames.managers.NicknameManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final AlchemyPersona plugin;
    private final NicknameManager nicknameManager;

    public PlayerListener(AlchemyPersona plugin, NicknameManager nicknameManager) {
        this.plugin = plugin;
        this.nicknameManager = nicknameManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("velocity-sync.enabled", false)) {
            // Delay slightly to ensure player is fully registered on proxy/channel
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    plugin.getMessagingHandler().requestPersona(event.getPlayer());
                }
            }, 5L);
        }
    }

    // Removed onChat renderer to prevent interference with dedicated chat plugins.
    // The nickname will still show up if the chat plugin uses player.getDisplayName() 
    // or the %AlchemyPersona_displayname% placeholder.
}
