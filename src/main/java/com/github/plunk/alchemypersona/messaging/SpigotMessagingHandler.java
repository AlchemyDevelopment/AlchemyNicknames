package com.github.plunk.alchemypersona.messaging;

import com.github.plunk.alchemypersona.AlchemyPersona;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.net.URI;
import java.util.UUID;

public class SpigotMessagingHandler {

    private final AlchemyPersona plugin;
    private SyncWebSocketClient wsClient;
    private boolean enabled = false;

    public SpigotMessagingHandler(AlchemyPersona plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("velocity-sync.enabled", false);
        if (enabled) {
            connectWebSocket();
        }
    }

    private void connectWebSocket() {
        String urlString = plugin.getConfig().getString("velocity-sync.websocket-url", "ws://localhost:9090");
        String secretToken = plugin.getConfig().getString("velocity-sync.secret-token", "my_secret_token_here");

        try {
            String uriStr = urlString;
            if (uriStr.contains("?")) {
                uriStr += "&token=" + secretToken;
            } else {
                uriStr += "?token=" + secretToken;
            }
            URI serverUri = new URI(uriStr);

            plugin.getLogger().info("Connecting AlchemyPersona Sync WebSocket to shared proxy host: " + urlString);
            wsClient = new SyncWebSocketClient(serverUri);
            wsClient.connect();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize sync WebSocket connection: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled() && enabled) {
                connectWebSocket();
            }
        }, 100L); // 5 seconds
    }

    public void requestPersona(Player player) {
        if (!enabled || wsClient == null || !wsClient.isOpen()) {
            return;
        }
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "persona");
        json.addProperty("action", "GET_PERSONA");
        json.addProperty("uuid", player.getUniqueId().toString());
        wsClient.send(json.toString());
    }

    public void updatePersona(Player player, String nickname, String pin, String tag, String jm) {
        if (!enabled || wsClient == null || !wsClient.isOpen()) {
            return;
        }
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "persona");
        json.addProperty("action", "UPDATE_PERSONA");
        json.addProperty("uuid", player.getUniqueId().toString());
        json.addProperty("nickname", nickname != null ? nickname : "");
        json.addProperty("selectedTag", tag != null ? tag : "");
        json.addProperty("selectedJoinMessage", jm != null ? jm : "");
        wsClient.send(json.toString());
    }

    public void registerSession(String token, UUID uuid) {
        if (!enabled || wsClient == null || !wsClient.isOpen()) {
            return;
        }
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "persona");
        json.addProperty("action", "REGISTER_SESSION");
        json.addProperty("token", token);
        json.addProperty("uuid", uuid.toString());
        wsClient.send(json.toString());
    }

    public void requestLinkCode(Player player) {
        if (!enabled || wsClient == null || !wsClient.isOpen()) {
            return;
        }
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "persona");
        json.addProperty("action", "GENERATE_LINK_CODE");
        json.addProperty("uuid", player.getUniqueId().toString());
        wsClient.send(json.toString());
    }

    public void shutdown() {
        if (wsClient != null) {
            try {
                wsClient.closedByPlugin = true;
                wsClient.close();
            } catch (Exception ignored) {}
        }
    }

    private void handleIncomingMessage(String message) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(message).getAsJsonObject();
            if (!json.has("type") || !"persona".equalsIgnoreCase(json.get("type").getAsString())) {
                return;
            }

            String action = json.get("action").getAsString();
            String uuidStr = json.get("uuid").getAsString();
            UUID uuid = UUID.fromString(uuidStr);

            if ("SET_PERSONA".equalsIgnoreCase(action)) {
                String nick = json.has("nickname") ? json.get("nickname").getAsString() : "";
                String tag = json.has("selectedTag") ? json.get("selectedTag").getAsString() : "";
                String jm = json.has("selectedJoinMessage") ? json.get("selectedJoinMessage").getAsString() : "";

                // Run on main Spigot thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getNicknameManager().setNicknameInMemory(uuid, nick);
                    plugin.getTagManager().setTagInMemory(uuid, tag);
                    com.github.plunk.alchemypersona.joinmessages.Data.setInMemory(uuid, jm);
                });
            } else if ("LINK_CODE_RESPONSE".equalsIgnoreCase(action)) {
                String code = json.get("code").getAsString();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                        online.sendMessage(mm.deserialize("<gray>Use this link code in the designer: <gradient:#00c6ff:#8000FF><bold>" + code + "</bold></gradient></gray>"));
                    }
                });
            } else if ("FETCH_PERSONA_DATA_REQUEST".equalsIgnoreCase(action)) {
                String requestId = json.has("requestId") ? json.get("requestId").getAsString() : "";
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var dataMap = plugin.buildPersonaDataMap(uuid);
                    com.google.gson.JsonObject resp = new com.google.gson.JsonObject();
                    resp.addProperty("type", "persona");
                    resp.addProperty("action", "FETCH_PERSONA_DATA_RESPONSE");
                    resp.addProperty("uuid", uuid.toString());
                    resp.addProperty("requestId", requestId);
                    
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    resp.add("data", gson.toJsonTree(dataMap));
                    if (wsClient != null && wsClient.isOpen()) {
                        wsClient.send(resp.toString());
                    }
                });
            } else if ("FETCH_ASSET_REQUEST".equalsIgnoreCase(action)) {
                String requestId = json.has("requestId") ? json.get("requestId").getAsString() : "";
                String namespace = json.has("namespace") ? json.get("namespace").getAsString() : "";
                String path = json.has("path") ? json.get("path").getAsString() : "";
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    java.io.File assetsFolder = new java.io.File(plugin.getDataFolder().getParentFile(), "Nexo/pack/assets");
                    java.io.File textureFile  = new java.io.File(assetsFolder, namespace + "/textures/" + path);
                    
                    com.google.gson.JsonObject resp = new com.google.gson.JsonObject();
                    resp.addProperty("type", "persona");
                    resp.addProperty("action", "FETCH_ASSET_RESPONSE");
                    resp.addProperty("requestId", requestId);
                    
                    if (textureFile.exists()) {
                        try {
                            byte[] bytes = java.nio.file.Files.readAllBytes(textureFile.toPath());
                            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                            resp.addProperty("found", true);
                            resp.addProperty("base64", base64);
                        } catch (Exception e) {
                            resp.addProperty("found", false);
                        }
                    } else {
                        resp.addProperty("found", false);
                    }
                    
                    if (wsClient != null && wsClient.isOpen()) {
                        wsClient.send(resp.toString());
                    }
                });
            }
        } catch (Exception e) {
            // Ignore other WebSocket traffic or errors
        }
    }

    private class SyncWebSocketClient extends org.java_websocket.client.WebSocketClient {
        private boolean closedByPlugin = false;

        public SyncWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(org.java_websocket.handshake.ServerHandshake handshakedata) {
            plugin.getLogger().info("Successfully connected to shared StaffChat WebSocket for data sync!");
        }

        @Override
        public void onMessage(String message) {
            handleIncomingMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            if (!closedByPlugin) {
                plugin.getLogger().warning("Persona Sync WebSocket connection closed. Retrying in 5 seconds...");
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            plugin.getLogger().severe("Error in Sync WebSocket client: " + ex.getMessage());
        }
    }
}
