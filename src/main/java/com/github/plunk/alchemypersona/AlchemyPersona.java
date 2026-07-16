package com.github.plunk.alchemypersona;

import com.github.plunk.alchemypersona.nicknames.commands.NicknameCommand;
import com.github.plunk.alchemypersona.commands.PersonaImportCommand;
import com.github.plunk.alchemypersona.commands.LinkPersonaCommand;
import com.github.plunk.alchemypersona.discord.LinkManager;
import com.github.plunk.alchemypersona.nicknames.listeners.PlayerListener;
import com.github.plunk.alchemypersona.nicknames.managers.NicknameManager;

import com.github.plunk.alchemypersona.pins.commands.PinsCommand;
import com.github.plunk.alchemypersona.pins.managers.PinManager;
import com.github.plunk.alchemypersona.pins.menu.MenuManager;

import com.github.plunk.alchemypersona.tags.managers.TagManager;
import com.github.plunk.alchemypersona.joinmessages.gui.GUIOptions;
import com.github.plunk.alchemypersona.placeholders.PersonaExpansion;

import com.github.plunk.alchemypersona.joinmessages.managers.MessageManager;
import com.github.plunk.alchemypersona.joinmessages.gui.MessageMenuManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class AlchemyPersona extends JavaPlugin {

    private static AlchemyPersona instance;

    // Nickname Module
    private NicknameManager nicknameManager;
    private io.javalin.Javalin server;
    private record Session(String uuid, long expiresAt) {}
    private record DiscordSession(String discordId, long expiresAt) {}
    private record LinkCode(java.util.UUID uuid, long expiresAt) {}
    private static final long SESSION_TTL_MS         = 10 * 60 * 1000L;
    private static final long DISCORD_SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000L;
    private static final long LINK_CODE_TTL_MS       = 10 * 60 * 1000L;
    private final java.util.Map<String, Session>        sessions        = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, DiscordSession> discordSessions = new java.util.concurrent.ConcurrentHashMap<>();
    // oauthStates: CSRF state token → expiry timestamp
    private final java.util.Map<String, Long>            oauthStates     = new java.util.concurrent.ConcurrentHashMap<>();
    // linkCodes: short code → LinkCode(uuid, expiry)  (from /linkpersona command)
    private final java.util.Map<String, LinkCode>        linkCodes       = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Random random = new java.util.Random();

    // Discord Module
    private LinkManager linkManager;

    // Pins Module
    private PinManager pinManager;
    private MenuManager pinsMenuManager;

    // Tags Module
    private TagManager tagManager;
    private com.github.plunk.alchemypersona.tags.menu.MenuManager tagsMenuManager;

    // Join Messages Module
    private MessageManager messageManager;
    private MessageMenuManager joinMessagesMenuManager;
    private GUIOptions joinMessagesGuiOptions;
    private java.util.Map<String, String> nexoMapping = new java.util.HashMap<>();

    // Configs
    private FileConfiguration nicknamesConfig;
    private FileConfiguration pinsConfig;
    private FileConfiguration tagsConfig;
    private FileConfiguration joinMessagesConfig;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("nicknames.yml", false);
        saveResource("pins.yml", false);
        saveResource("tags.yml", false);
        saveResource("join_messages.yml", false);
        saveResource("pins_menu.yml", false);
        saveResource("tags_menu.yml", false);
        saveResource("join_messages_menu.yml", false);

        loadModuleConfigs();

        getLogger().info("AlchemyPersona enable sequence started...");

        linkManager = new LinkManager(this);

        nicknameManager = new NicknameManager(this);
        nicknameManager.loadNicknames();
        registerNicknameCommands();
        registerImportCommand();
        registerLinkCommand();
        getServer().getPluginManager().registerEvents(new PlayerListener(this, nicknameManager), this);

        // 2. Pins
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            this.pinManager = new PinManager(this);
            this.pinsMenuManager = new MenuManager(this, pinManager);
            PinsCommand pinsExecutor = new PinsCommand(pinsMenuManager, pinManager);
            getCommand("pins").setExecutor(pinsExecutor);
            getCommand("pins").setTabCompleter(pinsExecutor);
            getServer().getPluginManager().registerEvents(pinsMenuManager, this);
            getLogger().info("Pins module initialized.");
        }

        // 3. Tags
        this.tagManager = new TagManager(this);
        this.tagsMenuManager = new com.github.plunk.alchemypersona.tags.menu.MenuManager(this, tagManager);
        com.github.plunk.alchemypersona.tags.commands.TagsCommand tagsCommand =
            new com.github.plunk.alchemypersona.tags.commands.TagsCommand(this, tagsMenuManager, tagManager);
        getCommand("tags").setExecutor(tagsCommand);
        getCommand("tags").setTabCompleter(tagsCommand);
        getServer().getPluginManager().registerEvents(
            new com.github.plunk.alchemypersona.tags.listeners.TagListener(this, tagManager), this);
        getServer().getPluginManager().registerEvents(tagsMenuManager, this);
        getLogger().info("Tags module initialized.");

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            boolean registered = new PersonaExpansion(this).register();
            if (registered) getLogger().info("PersonaExpansion registered with PlaceholderAPI.");
            else getLogger().warning("PersonaExpansion FAILED to register with PlaceholderAPI!");
        } else {
            getLogger().info("PlaceholderAPI not found or not enabled yet.");
        }

        // 4. Join Messages
        com.github.plunk.alchemypersona.joinmessages.Data.setup(this);
        this.messageManager = new MessageManager(this);
        this.messageManager.loadMessages();
        this.joinMessagesGuiOptions = new GUIOptions(this);
        this.joinMessagesMenuManager = new MessageMenuManager(this);
        getServer().getPluginManager().registerEvents(this.joinMessagesMenuManager, this);
        getServer().getPluginManager().registerEvents(
            new com.github.plunk.alchemypersona.joinmessages.listeners.JoinListener(this), this);
        com.github.plunk.alchemypersona.joinmessages.commands.CommandAlchemyJoinMessages ajmHandler =
            new com.github.plunk.alchemypersona.joinmessages.commands.CommandAlchemyJoinMessages(this);
        getCommand("alchemyjoinmessages").setExecutor(ajmHandler);
        getCommand("alchemyjoinmessages").setTabCompleter(ajmHandler);
        getLogger().info("Join Messages module initialized.");

        getLogger().info("Synchronous enable sequence complete. Scheduling delayed tasks...");

        getServer().getScheduler().runTask(this, () -> {
            getLogger().info("Delayed initialization task started...");
            startWebServer();

            // Expire game sessions every 5 min
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> sessions.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().expiresAt()),
                6000L, 6000L);
            // Expire Discord sessions, link codes, and stale OAuth states hourly
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                long now = System.currentTimeMillis();
                discordSessions.entrySet().removeIf(e -> now > e.getValue().expiresAt());
                linkCodes.entrySet().removeIf(e -> now > e.getValue().expiresAt());
                oauthStates.entrySet().removeIf(e -> now > e.getValue());
            }, 72000L, 72000L);

            loadNexoGlyphs();
            loadSessions();

            getLogger().info("AlchemyPersona v" + getDescription().getVersion() + " is fully enabled and ready!");
        });
    }

    // ─── Command Registration ────────────────────────────────────────────────

    private void registerNicknameCommands() {
        NicknameCommand nicknameExecutor = new NicknameCommand(this, nicknameManager);
        getCommand("nickname").setExecutor(nicknameExecutor);
        getCommand("nickname").setTabCompleter(nicknameExecutor);
        getCommand("unnick").setExecutor(nicknameExecutor);
        getCommand("unnick").setTabCompleter(nicknameExecutor);
        getCommand("nicknameeditor").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }

            String token;
            do { token = String.format("%05d", random.nextInt(100000)); } while (sessions.containsKey(token));
            sessions.put(token, new Session(player.getUniqueId().toString(), System.currentTimeMillis() + SESSION_TTL_MS));

            String editorUrl = getConfig().getString("web.editor-url", "https://plunk.github.io/AlchemyPersona");
            String apiBase   = getConfig().getString("web.base-url", "https://stats.bloc.kz");
            if (editorUrl.endsWith("/")) editorUrl = editorUrl.substring(0, editorUrl.length() - 1);
            if (apiBase.endsWith("/"))   apiBase   = apiBase.substring(0, apiBase.length() - 1);
            String link = editorUrl + "/?player=" + player.getName() + "&token=" + token + "&api=" + apiBase;

            var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
            player.sendMessage(mm.deserialize("      <gradient:#FF0080:#8000FF><bold>✦ Persona Designer ✦</bold></gradient>"));
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>Design your own <gradient:#FF0080:#8000FF>RGB gradient</gradient> identity</gray>"));
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>Your session expires in <white>10 minutes</white></gray>"));
            player.sendMessage(net.kyori.adventure.text.Component.empty());
            player.sendMessage(mm.deserialize("      <click:open_url:'" + link + "'><hover:show_text:'<gray>Opens in your browser'><gradient:#00c6ff:#8000FF><bold>[  ✦ Open Designer  ]</bold></gradient></hover></click>"));
            player.sendMessage(net.kyori.adventure.text.Component.empty());
            player.sendMessage(mm.deserialize(" <dark_gray>»</dark_gray> <gray>Bedrock / can't click? Visit the site and enter:</gray>"));
            player.sendMessage(mm.deserialize("   <dark_gray>Name: </dark_gray><white>" + player.getName() + "</white>   <dark_gray>Code: </dark_gray><gradient:#00c6ff:#8000FF><bold>" + token + "</bold></gradient>"));
            player.sendMessage(mm.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
            return true;
        });
    }

    private void registerImportCommand() {
        PersonaImportCommand importExecutor = new PersonaImportCommand(this);
        getCommand("personaimport").setExecutor(importExecutor);
        getCommand("personaimport").setTabCompleter(importExecutor);
    }

    private void registerLinkCommand() {
        LinkPersonaCommand linkExecutor = new LinkPersonaCommand(this);
        getCommand("linkpersona").setExecutor(linkExecutor);
        getCommand("linkpersona").setTabCompleter(linkExecutor);
    }

    // ─── Discord OAuth helpers ───────────────────────────────────────────────

    /** Called by /linkpersona — generates and stores a short link code, returned to the player. */
    public String generateLinkCode(java.util.UUID uuid) {
        // Remove any existing codes for this player so they can't accumulate
        linkCodes.entrySet().removeIf(e -> e.getValue().uuid().equals(uuid));
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I to avoid confusion
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        String code = sb.toString();
        linkCodes.put(code, new LinkCode(uuid, System.currentTimeMillis() + LINK_CODE_TTL_MS));
        return code;
    }

    /** Exchanges a Discord OAuth code for the user's Discord snowflake ID. */
    private String exchangeCodeForDiscordId(String code, String redirectUri) throws Exception {
        String clientId     = getConfig().getString("discord.client-id", "");
        String clientSecret = getConfig().getString("discord.client-secret", "");

        var client = java.net.http.HttpClient.newHttpClient();

        String body = "client_id=" + clientId
            + "&client_secret=" + clientSecret
            + "&grant_type=authorization_code"
            + "&code=" + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8)
            + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8);

        var tokenReq = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://discord.com/api/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            .build();
        var tokenBody = client.send(tokenReq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        var tokenJson = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tokenBody);
        if (!tokenJson.has("access_token"))
            throw new RuntimeException("Discord token exchange failed: " + tokenBody);
        String accessToken = tokenJson.get("access_token").asText();

        var userReq = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://discord.com/api/users/@me"))
            .header("Authorization", "Bearer " + accessToken)
            .build();
        var userBody = client.send(userReq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        var userJson = new com.fasterxml.jackson.databind.ObjectMapper().readTree(userBody);
        return userJson.get("id").asText();
    }

    private String randomToken(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        var sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    // ─── Web Server ──────────────────────────────────────────────────────────

    private void startWebServer() {
        if (server != null) return;

        int port = getConfig().getInt("web.port", 8085);

        // Implement JsonMapper directly to avoid JavalinJackson's lazy Class.forName()
        // scanning which triggers "zip file closed" via Paper's reflection proxy.
        final com.fasterxml.jackson.databind.ObjectMapper om =
            new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final io.javalin.json.JsonMapper jsonMapper = new io.javalin.json.JsonMapper() {
            @Override
            public String toJsonString(Object obj, java.lang.reflect.Type type) {
                try { return om.writeValueAsString(obj); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override @SuppressWarnings("unchecked")
            public <T> T fromJsonString(String json, java.lang.reflect.Type targetType) {
                try { return (T) om.readValue(json, om.constructType(targetType)); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
        };

        server = io.javalin.Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(jsonMapper);
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
        });

        server.get("/health", ctx -> ctx.result("AlchemyPersona API is UP"));

        // ── Discord OAuth: initiate ──────────────────────────────────────────
        server.get("/auth/discord", ctx -> {
            String clientId = getConfig().getString("discord.client-id", "");
            if (clientId.isBlank()) { ctx.status(503).result("Discord not configured"); return; }

            String apiBase = getConfig().getString("web.base-url", "");
            if (apiBase.endsWith("/")) apiBase = apiBase.substring(0, apiBase.length() - 1);

            String state = randomToken(20);
            oauthStates.put(state, System.currentTimeMillis() + 10 * 60 * 1000L);

            String proxyPrefix = getConfig().getString("web.proxy-prefix", "/api/nickname");
            if (proxyPrefix.endsWith("/")) proxyPrefix = proxyPrefix.substring(0, proxyPrefix.length() - 1);
            if (!proxyPrefix.isEmpty() && !proxyPrefix.startsWith("/")) proxyPrefix = "/" + proxyPrefix;

            String redirectUri = java.net.URLEncoder.encode(
                apiBase + proxyPrefix + "/auth/discord/callback", java.nio.charset.StandardCharsets.UTF_8);
            ctx.redirect("https://discord.com/api/oauth2/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=identify"
                + "&state=" + state);
        });

        // ── Discord OAuth: callback ──────────────────────────────────────────
        server.get("/auth/discord/callback", ctx -> {
            String code  = ctx.queryParam("code");
            String state = ctx.queryParam("state");

            String apiBase   = getConfig().getString("web.base-url", "");
            String editorUrl = getConfig().getString("web.editor-url", "");
            if (apiBase.endsWith("/"))   apiBase   = apiBase.substring(0, apiBase.length() - 1);
            if (editorUrl.endsWith("/")) editorUrl = editorUrl.substring(0, editorUrl.length() - 1);

            Long stateExpiry = state != null ? oauthStates.remove(state) : null;
            if (code == null || stateExpiry == null || System.currentTimeMillis() > stateExpiry) {
                ctx.redirect(editorUrl + "/?error=oauth_failed");
                return;
            }

            String discordId;
            try {
                String proxyPrefix = getConfig().getString("web.proxy-prefix", "/api/nickname");
                if (proxyPrefix.endsWith("/")) proxyPrefix = proxyPrefix.substring(0, proxyPrefix.length() - 1);
                if (!proxyPrefix.isEmpty() && !proxyPrefix.startsWith("/")) proxyPrefix = "/" + proxyPrefix;

                discordId = exchangeCodeForDiscordId(code, apiBase + proxyPrefix + "/auth/discord/callback");
            } catch (Exception e) {
                getLogger().warning("Discord OAuth error: " + e.getMessage());
                ctx.redirect(editorUrl + "/?error=oauth_failed");
                return;
            }

            String dsession = randomToken(32);
            discordSessions.put(dsession, new DiscordSession(discordId, System.currentTimeMillis() + DISCORD_SESSION_TTL_MS));
            String encodedApi = java.net.URLEncoder.encode(apiBase, java.nio.charset.StandardCharsets.UTF_8);
            ctx.redirect(editorUrl + "/?dsession=" + dsession + "&api=" + encodedApi);
        });

        // ── Linked accounts list (for Discord login) ─────────────────────────
        server.get("/accounts", ctx -> {
            String dsessionToken = ctx.queryParam("dsession");
            if (dsessionToken == null) { ctx.status(400).result("Missing dsession"); return; }
            DiscordSession dsess = discordSessions.get(dsessionToken);
            if (dsess == null || System.currentTimeMillis() > dsess.expiresAt()) {
                ctx.status(401).result("Invalid or expired Discord session"); return;
            }

            var uuids    = linkManager.getLinkedAccounts(dsess.discordId());
            var accounts = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (java.util.UUID uuid : uuids) {
                var acc = new java.util.HashMap<String, Object>();
                acc.put("uuid", uuid.toString());
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                acc.put("name", op.getName() != null ? op.getName() : uuid.toString());
                accounts.add(acc);
            }
            ctx.json(accounts); // plain array
        });

        // ── Link a Minecraft account via code (from /linkpersona) ────────────
        server.post("/link-code", ctx -> {
            try {
                @SuppressWarnings("unchecked")
                var body = (java.util.Map<String, String>) ctx.bodyAsClass(java.util.Map.class);
                String dsessionToken = body.get("dsession");
                String code          = body.get("code");
                if (dsessionToken == null || code == null) {
                    ctx.status(400).result("Missing dsession or code"); return;
                }

                DiscordSession dsess = discordSessions.get(dsessionToken);
                if (dsess == null || System.currentTimeMillis() > dsess.expiresAt()) {
                    ctx.status(401).result("Invalid or expired Discord session"); return;
                }

                LinkCode lc = linkCodes.remove(code.toUpperCase());
                if (lc == null || System.currentTimeMillis() > lc.expiresAt()) {
                    ctx.status(400).result("Invalid or expired link code"); return;
                }

                linkManager.link(dsess.discordId(), lc.uuid());

                org.bukkit.entity.Player online = Bukkit.getPlayer(lc.uuid());
                if (online != null) {
                    online.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<green>✔ Your Discord account has been linked! You can now sign in at the Persona Designer.</green>"));
                }

                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(lc.uuid());
                var resp = new java.util.HashMap<String, String>();
                resp.put("uuid", lc.uuid().toString());
                resp.put("name", op.getName() != null ? op.getName() : lc.uuid().toString());
                ctx.json(resp);
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        // ── Persona data (token or dsession + uuid) ──────────────────────────
        server.get("/data", ctx -> {
            java.util.UUID uuid = resolveUuid(ctx);
            if (uuid == null) return; // resolveUuid already wrote the error response

            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

            net.luckperms.api.model.user.User lpUser = null;
            var lp = getLuckPerms();
            if (lp != null) {
                lpUser = lp.getUserManager().getUser(uuid);
                if (lpUser == null) lpUser = lp.getUserManager().loadUser(uuid).join();
            }

            var data = new java.util.HashMap<String, Object>();
            data.put("playerName", offlinePlayer.getName());
            data.put("nickname", nicknameManager.getNickname(uuid));

            final net.luckperms.api.model.user.User finalLpUser = lpUser;
            java.util.function.Predicate<String> hasPerm = perm ->
                finalLpUser != null && finalLpUser.getCachedData().getPermissionData()
                    .checkPermission(perm).asBoolean();

            // Pins
            var pins = new java.util.ArrayList<java.util.Map<String, Object>>();
            try {
                if (pinManager != null && getPinsConfig() != null) {
                    String currentPin = null;
                    if (offlinePlayer.isOnline()) {
                        currentPin = pinManager.getCurrentPin(offlinePlayer.getPlayer());
                    } else if (finalLpUser != null) {
                        currentPin = finalLpUser.getCachedData().getMetaData().getSuffix();
                    }
                    var section = getPinsConfig().getConfigurationSection("pins");
                    if (section != null) {
                        for (String pinId : section.getKeys(false)) {
                            var pinData = new java.util.HashMap<String, Object>();
                            pinData.put("id", pinId);
                            pinData.put("displayName", getPinsConfig().getString("pins." + pinId + ".display_name"));
                            String unicode = getPinsConfig().getString("pins." + pinId + ".pin_unicode");
                            pinData.put("unicode", unicode);
                            String explicitTexture = getPinsConfig().getString("pins." + pinId + ".nexo_texture");
                            if (explicitTexture != null && explicitTexture.contains(":")) {
                                String[] parts = explicitTexture.split(":");
                                pinData.put("imageUrl", "/api/nickname/assets/" + parts[0] + "/textures/" + parts[1] + ".png");
                            }
                            if (!pinData.containsKey("imageUrl") && unicode != null) {
                                String stripped = org.bukkit.ChatColor.stripColor(
                                    org.bukkit.ChatColor.translateAlternateColorCodes('&', unicode)).trim();
                                if (nexoMapping.containsKey(stripped))
                                    pinData.put("imageUrl", nexoMapping.get(stripped));
                            }
                            if (!pinData.containsKey("imageUrl")) {
                                String lowerId = pinId.toLowerCase();
                                if (nexoMapping.containsKey(lowerId))
                                    pinData.put("imageUrl", nexoMapping.get(lowerId));
                            }
                            pinData.put("owned", hasPerm.test("LPP.pin." + pinId));
                            pinData.put("selected", pinId.equals(currentPin)
                                || (currentPin != null && currentPin.equals(unicode)));
                            pins.add(pinData);
                        }
                    }
                }
            } catch (Exception e) { getLogger().warning("Error loading Pins for API: " + e.getMessage()); }
            data.put("pins", pins);

            // Tags
            var tags = new java.util.ArrayList<java.util.Map<String, Object>>();
            try {
                if (tagManager != null && getTagsConfig() != null) {
                    String currentTagId = tagManager.getPlayerTagId(uuid);
                    var tagSection = getTagsConfig().getConfigurationSection("tags");
                    if (tagSection != null) {
                        for (String tagId : tagSection.getKeys(false)) {
                            var tagData = new java.util.HashMap<String, Object>();
                            tagData.put("id", tagId);
                            tagData.put("displayName", getTagsConfig().getString("tags." + tagId + ".display_name"));
                            tagData.put("tag", getTagsConfig().getString("tags." + tagId + ".tag"));
                            String perm = getTagsConfig().getString("tags." + tagId + ".permission", "deluxetags.tag." + tagId);
                            tagData.put("owned", hasPerm.test(perm));
                            tagData.put("selected", tagId.equals(currentTagId));
                            tags.add(tagData);
                        }
                    }
                }
            } catch (Exception e) { getLogger().warning("Error loading Tags for API: " + e.getMessage()); }
            data.put("tags", tags);

            // Join Messages
            var jms = new java.util.ArrayList<java.util.Map<String, Object>>();
            try {
                if (messageManager != null) {
                    String currentJm = com.github.plunk.alchemypersona.joinmessages.Data.get()
                        .getString("players." + uuid);
                    String pName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";
                    for (var jm : messageManager.getLoadedMessages()) {
                        var jmData = new java.util.HashMap<String, Object>();
                        jmData.put("id", jm.getIdentifier());
                        jmData.put("text", jm.getFormattedMessage(pName));
                        jmData.put("owned", hasPerm.test(jm.getPermission()));
                        jmData.put("selected", jm.getIdentifier().equals(currentJm));
                        jms.add(jmData);
                    }
                }
            } catch (Exception e) { getLogger().warning("Error loading Join Messages for API: " + e.getMessage()); }
            data.put("joinMessages", jms);

            ctx.json(data);
        });

        // ── Nexo assets ──────────────────────────────────────────────────────
        server.get("/api/nickname/assets/{namespace}/textures/{path}", ctx -> {
            String namespace  = ctx.pathParam("namespace");
            String path       = ctx.pathParam("path");
            java.io.File assetsFolder = new java.io.File(getDataFolder().getParentFile(), "Nexo/pack/assets");
            java.io.File textureFile  = new java.io.File(assetsFolder, namespace + "/textures/" + path);
            if (textureFile.exists()) {
                ctx.contentType("image/png");
                ctx.result(new java.io.FileInputStream(textureFile));
            } else {
                ctx.status(404);
            }
        });

        // ── Save ─────────────────────────────────────────────────────────────
        server.post("/save", ctx -> {
            try {
                SaveRequest req = ctx.bodyAsClass(SaveRequest.class);
                if (req == null) { ctx.status(400).result("Bad Request"); return; }

                java.util.UUID uuid;
                if (req.token != null) {
                    Session session = sessions.get(req.token);
                    if (session == null || System.currentTimeMillis() > session.expiresAt()) {
                        ctx.status(401).result("Invalid or expired session token"); return;
                    }
                    uuid = java.util.UUID.fromString(session.uuid());
                } else if (req.dsession != null && req.uuid != null) {
                    DiscordSession dsess = discordSessions.get(req.dsession);
                    if (dsess == null || System.currentTimeMillis() > dsess.expiresAt()) {
                        ctx.status(401).result("Invalid or expired Discord session"); return;
                    }
                    uuid = java.util.UUID.fromString(req.uuid);
                    if (!linkManager.getLinkedAccounts(dsess.discordId()).contains(uuid)) {
                        ctx.status(403).result("UUID not linked to this Discord account"); return;
                    }
                } else {
                    ctx.status(400).result("Missing token or dsession+uuid"); return;
                }

                org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);

                if (req.nickname != null)
                    nicknameManager.setNickname(uuid, req.nickname);

                if (req.selectedPin != null && player != null) {
                    if (req.selectedPin.isEmpty()) {
                        pinManager.clearPin(player);
                    } else {
                        var unicode = getPinsConfig().getString("pins." + req.selectedPin + ".pin_unicode");
                        if (unicode != null) pinManager.setPin(player, unicode);
                    }
                }

                if (req.selectedTag != null && player != null) {
                    if (req.selectedTag.isEmpty()) tagManager.clearTag(player);
                    else tagManager.setTag(player, req.selectedTag);
                }

                if (req.selectedJoinMessage != null) {
                    if (req.selectedJoinMessage.isEmpty())
                        com.github.plunk.alchemypersona.joinmessages.Data.get().set("players." + uuid, null);
                    else
                        com.github.plunk.alchemypersona.joinmessages.Data.get().set("players." + uuid, req.selectedJoinMessage);
                    com.github.plunk.alchemypersona.joinmessages.Data.save();
                }

                ctx.status(200).result("OK");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        server.get("/current", ctx -> {
            String token = ctx.queryParam("token");
            if (token == null) { ctx.status(400).result("Missing token"); return; }
            Session session = sessions.get(token);
            if (session == null || System.currentTimeMillis() > session.expiresAt()) {
                ctx.status(401).result("Invalid or expired token"); return;
            }
            java.util.UUID uuid = java.util.UUID.fromString(session.uuid());
            String nick = nicknameManager.getNickname(uuid);
            ctx.contentType("application/json");
            ctx.result("{\"nickname\":" + (nick != null
                ? "\"" + nick.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                : "null") + "}");
        });

        try {
            server.start(port);
            getLogger().info("Web server started on port " + port);
        } catch (Exception e) {
            getLogger().severe("Could not start web server! Port " + port + " is already in use.");
            getLogger().severe("Identity Designer will be unavailable until this is fixed.");
            server = null;
        }
    }

    /**
     * Resolves the target UUID from either a game token (?token=) or Discord session
     * (?dsession= + ?uuid=). Writes the error response and returns null on failure.
     */
    private java.util.UUID resolveUuid(io.javalin.http.Context ctx) throws Exception {
        String token    = ctx.queryParam("token");
        String dsession = ctx.queryParam("dsession");
        String uuidStr  = ctx.queryParam("uuid");

        if (token != null) {
            Session session = sessions.get(token);
            if (session == null || System.currentTimeMillis() > session.expiresAt()) {
                ctx.status(401).result("Invalid or expired token");
                return null;
            }
            try { return java.util.UUID.fromString(session.uuid()); }
            catch (Exception e) { ctx.status(400).result("Invalid UUID in session"); return null; }
        }

        if (dsession != null && uuidStr != null) {
            DiscordSession dsess = discordSessions.get(dsession);
            if (dsess == null || System.currentTimeMillis() > dsess.expiresAt()) {
                ctx.status(401).result("Invalid or expired Discord session");
                return null;
            }
            java.util.UUID uuid;
            try { uuid = java.util.UUID.fromString(uuidStr); }
            catch (Exception e) { ctx.status(400).result("Invalid UUID"); return null; }
            if (!linkManager.getLinkedAccounts(dsess.discordId()).contains(uuid)) {
                ctx.status(403).result("UUID not linked to this Discord account");
                return null;
            }
            return uuid;
        }

        ctx.status(400).result("Missing token or dsession+uuid");
        return null;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void onDisable() {
        if (server != null) server.stop();
        if (nicknameManager != null) nicknameManager.saveNicknames();
        saveSessions();
        instance = null;
        getLogger().info("AlchemyPersona has been disabled.");
    }

    public void reload() {
        reloadConfig();
        loadModuleConfigs();
        nicknameManager.loadSettings();
        nicknameManager.loadNicknames();
        if (pinManager != null) pinsMenuManager.loadMenu();
        if (tagManager != null) { tagManager.reload(); tagsMenuManager.reload(); }
        if (messageManager != null) {
            com.github.plunk.alchemypersona.joinmessages.Data.reload();
            messageManager.loadMessages();
            joinMessagesMenuManager.loadMenu();
        }
    }

    private void loadModuleConfigs() {
        nicknamesConfig    = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "nicknames.yml"));
        pinsConfig         = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "pins.yml"));
        tagsConfig         = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "tags.yml"));
        joinMessagesConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "join_messages.yml"));
    }

    private void saveSessions() {
        File file = new File(getDataFolder(), "sessions.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        discordSessions.forEach((token, sess) -> {
            cfg.set("discord." + token + ".id", sess.discordId());
            cfg.set("discord." + token + ".expiry", sess.expiresAt());
        });
        try { cfg.save(file); } catch (Exception e) { getLogger().warning("Failed to save sessions: " + e.getMessage()); }
    }

    private void loadSessions() {
        File file = new File(getDataFolder(), "sessions.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection("discord");
        if (section == null) return;
        long now = System.currentTimeMillis();
        for (String token : section.getKeys(false)) {
            String id = cfg.getString("discord." + token + ".id");
            long expiry = cfg.getLong("discord." + token + ".expiry");
            if (id != null && expiry > now) {
                discordSessions.put(token, new DiscordSession(id, expiry));
            }
        }
        getLogger().info("Loaded " + discordSessions.size() + " active Discord sessions.");
    }

    private void loadNexoGlyphs() {
        java.io.File nexoFolder = new java.io.File(getDataFolder().getParentFile(), "Nexo/glyphs");
        if (!nexoFolder.exists()) return;
        java.io.File[] files = nexoFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (java.io.File file : files) {
            org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            for (String key : cfg.getKeys(false)) {
                String charStr = cfg.getString(key + ".char");
                String texture = cfg.getString(key + ".texture");
                if (charStr != null && texture != null) {
                    String[] parts = texture.split(":");
                    if (parts.length == 2) {
                        String imageUrl = "/api/nickname/assets/" + parts[0] + "/textures/" + parts[1] + ".png";
                        nexoMapping.put(charStr, imageUrl);
                        nexoMapping.put(key.toLowerCase().replaceAll("_\\d+$", ""), imageUrl);
                    }
                }
            }
        }
        getLogger().info("Loaded " + nexoMapping.size() + " Nexo glyph mappings.");
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public static AlchemyPersona getInstance()              { return instance; }
    public NicknameManager getNicknameManager()             { return nicknameManager; }
    public PinManager getPinManager()                       { return pinManager; }
    public TagManager getTagManager()                       { return tagManager; }
    public MessageManager getMessageManager()               { return messageManager; }
    public MessageMenuManager getJoinMessagesMenuManager()  { return joinMessagesMenuManager; }
    public GUIOptions getJoinMessagesGuiOptions()           { return joinMessagesGuiOptions; }
    public LinkManager getLinkManager()                     { return linkManager; }
    public FileConfiguration getNicknamesConfig()           { return nicknamesConfig; }
    public FileConfiguration getPinsConfig()                { return pinsConfig; }
    public FileConfiguration getTagsConfig()                { return tagsConfig; }
    public FileConfiguration getJoinMessagesConfig()        { return joinMessagesConfig; }

    public net.luckperms.api.LuckPerms getLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null)
            return net.luckperms.api.LuckPermsProvider.get();
        return null;
    }

    // ─── Inner types ─────────────────────────────────────────────────────────

    public static class SaveRequest {
        public String token;
        public String dsession;
        public String uuid;
        public String nickname;
        public String selectedPin;
        public String selectedTag;
        public String selectedJoinMessage;
    }
}
