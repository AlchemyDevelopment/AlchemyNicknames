package com.github.plunk.alchemypersona.tags.managers;

import com.github.plunk.alchemypersona.AlchemyPersona;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;
import java.util.concurrent.CompletableFuture;

public class LuckPermsHook {

    public static CompletableFuture<Boolean> grantTagPermission(AlchemyPersona plugin, Player player, String permission) {
        LuckPerms lp = plugin.getLuckPerms();
        if (lp == null) {
            return CompletableFuture.completedFuture(false);
        }

        return lp.getUserManager().modifyUser(player.getUniqueId(), user -> {
            Node node = Node.builder(permission).build();
            user.data().add(node);
        }).thenApply(v -> true);
    }
}
