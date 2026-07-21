package com.github.plunk.alchemypersona.commands;

import com.github.plunk.alchemypersona.AlchemyPersona;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LinkPersonaCommand implements CommandExecutor, TabCompleter {

    private final AlchemyPersona plugin;

    public LinkPersonaCommand(AlchemyPersona plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String clientId = plugin.getConfig().getString("discord.client-id", "");
        if (clientId.isBlank()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>Discord integration is not configured on this server.</red>"));
            return true;
        }

        var mm = MiniMessage.miniMessage();

        if (args.length > 0 && args[0].equalsIgnoreCase("unlink")) {
            plugin.getLinkManager().unlink(player.getUniqueId());
            player.sendMessage(mm.deserialize("<green>Your Discord account has been unlinked.</green>"));
            return true;
        }

        boolean linked = plugin.getLinkManager().isLinked(player.getUniqueId());

        if (plugin.getConfig().getBoolean("velocity-sync.enabled", false) && plugin.getMessagingHandler() != null) {
            plugin.getMessagingHandler().requestLinkCode(player);
            return true;
        }

        String code = plugin.generateLinkCode(player.getUniqueId());

        String editorUrl = plugin.getConfig().getString("web.editor-url", "https://nickname.bloc.kz");
        if (editorUrl.endsWith("/")) editorUrl = editorUrl.substring(0, editorUrl.length() - 1);

        player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        player.sendMessage(mm.deserialize("      <gradient:#5865F2:#7289DA><bold>✦ Link Discord Account ✦</bold></gradient>"));
        if (linked) {
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <green>Your account is currently linked.</green>"));
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>Use <white>/linkpersona unlink</white> to remove the link.</gray>"));
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>Or re-link below to switch Discord accounts.</gray>"));
        } else {
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>Log in with Discord on the web app,</gray>"));
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>then enter this code when prompted.</gray>"));
        }
        player.sendMessage(net.kyori.adventure.text.Component.empty());
        player.sendMessage(mm.deserialize("   <gray>Your link code:</gray>"));
        player.sendMessage(mm.deserialize("   <gradient:#5865F2:#7289DA><bold>" + code + "</bold></gradient>"
            + "  <dark_gray>(expires in 10 minutes)</dark_gray>"));
        player.sendMessage(net.kyori.adventure.text.Component.empty());
        player.sendMessage(mm.deserialize("   <gray>Visit <white>" + editorUrl + "</white> to link.</gray>"));
        player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("unlink");
        return List.of();
    }
}
