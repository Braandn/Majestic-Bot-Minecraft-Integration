package bot.majestic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import bot.majestic.utils.MessageFormatter;
import bot.majestic.utils.VersionChecker;

public class MajesticBot extends JavaPlugin {

  private static MajesticBot instance;

  // ── Shared config ──────────────────────────────────────────────────────────
  private static String SERVER_NAME = "";

  // ── WebSocket mode ─────────────────────────────────────────────────────────
  private static String WS_URL = "";
  private static String API_KEY = "";
  private static String API_SECRET = "";

  // ── Webhook mode ───────────────────────────────────────────────────────────
  private static String WEBHOOK_URL = "";

  // ── Per-event config ───────────────────────────────────────────────────────
  private final Map<String, Boolean> eventEnabled = new HashMap<>();
  private final Map<String, String> eventColors = new HashMap<>();
  private final Map<String, String> eventMessages = new HashMap<>();

  private static final String[] EVENT_KEYS = {
    "server_start",
    "server_stop",
    "player_chat",
    "player_join",
    "player_leave",
    "player_death",
    "player_advancement"
  };

  // ── Discord → Minecraft relay (WebSocket mode only) ───────────────────────
  private boolean discordChatEnabled = true;
  private String discordChatFormat = "&9[Discord] &f%discord_user%&7: &f%message%";

  // ── Discord commands (WebSocket mode only) ─────────────────────────────────
  private final Map<String, Boolean> commandEnabled = new HashMap<>();
  private final Map<String, List<String>> commandRoleIds = new HashMap<>();
  private final Map<String, List<String>> commandUserIds = new HashMap<>();

  private static final String[] COMMAND_KEYS = {"ban", "kick", "whitelist", "info"};

  // ── Active senders (only one is non-null at runtime) ──────────────────────
  private WebSocketManager wsManager;
  private WebhookSender webhookSender;

  private VersionChecker versionChecker;

  // ── Accessors ──────────────────────────────────────────────────────────────

  public VersionChecker getVersionChecker() {
    return versionChecker;
  }

  public static String getServerName() {
    return SERVER_NAME;
  }

  public boolean isDiscordChatEnabled() {
    return discordChatEnabled;
  }

  public String getDiscordChatFormat() {
    return discordChatFormat;
  }

  public boolean isCommandEnabled(String command) {
    return commandEnabled.getOrDefault(command.toLowerCase(), false);
  }

  /**
   * Returns {@code true} if the Discord user (by ID or any of their role IDs) is allowed to run
   * the given command. If both allowed lists are empty the command is open to everyone.
   */
  public boolean isCommandAuthorized(String command, String userId, List<String> roleIds) {
    List<String> allowedUsers = commandUserIds.getOrDefault(command.toLowerCase(), Collections.emptyList());
    List<String> allowedRoles = commandRoleIds.getOrDefault(command.toLowerCase(), Collections.emptyList());
    if (allowedUsers.isEmpty() && allowedRoles.isEmpty()) return true;
    if (!userId.isEmpty() && allowedUsers.contains(userId)) return true;
    if (roleIds != null) {
      for (String roleId : roleIds) {
        if (allowedRoles.contains(roleId)) return true;
      }
    }
    return false;
  }

  /**
   * Builds the {@code RegisterCommands} JSON frame sent to the Majestic API on every WebSocket
   * open (including after reconnects and reloads). The bot uses this to register Discord slash
   * commands and enforce role/user permission checks on the Discord side.
   */
  public String buildCommandsJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"event_type\":\"RegisterCommands\",\"commands\":{");
    for (int i = 0; i < COMMAND_KEYS.length; i++) {
      String cmd = COMMAND_KEYS[i];
      if (i > 0) sb.append(",");
      sb.append("\"").append(cmd).append("\":{");
      sb.append("\"enabled\":").append(commandEnabled.getOrDefault(cmd, false)).append(",");
      sb.append("\"allowed_role_ids\":[");
      appendJsonStringArray(sb, commandRoleIds.getOrDefault(cmd, Collections.emptyList()));
      sb.append("],\"allowed_user_ids\":[");
      appendJsonStringArray(sb, commandUserIds.getOrDefault(cmd, Collections.emptyList()));
      sb.append("]}");
    }
    sb.append("}}");
    return sb.toString();
  }

  private void appendJsonStringArray(StringBuilder sb, List<String> list) {
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("\"").append(esc(list.get(i))).append("\"");
    }
  }

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    loadConfigData();

    PluginManager pm = Bukkit.getPluginManager();
    pm.registerEvents(new Events(this), this);
    getCommand("majestic").setExecutor(new Reload(this));

    versionChecker = new VersionChecker(this);
    versionChecker.start();

    String key = "server_start";
    if (isEventEnabled(key)) {
      Map<String, String> vars = new HashMap<>();
      vars.put("server_name", SERVER_NAME);
      vars.put("online_players", String.valueOf(Bukkit.getOnlinePlayers().size()));
      vars.put("max_players", String.valueOf(Bukkit.getMaxPlayers()));
      String formatted = MessageFormatter.format(getMessage(key), vars);
      // In WebSocket mode this is queued until the handshake completes.
      sendEvent("ServerStart", "", "", formatted, getEventColor(key));
    }
  }

  @Override
  public void onDisable() {
    String key = "server_stop";
    if (isEventEnabled(key)) {
      Map<String, String> vars = new HashMap<>();
      vars.put("server_name", SERVER_NAME);
      vars.put("online_players", String.valueOf(Bukkit.getOnlinePlayers().size()));
      vars.put("max_players", String.valueOf(Bukkit.getMaxPlayers()));
      String formatted = MessageFormatter.format(getMessage(key), vars);
      sendEventSync("ServerStop", "", "", formatted, getEventColor(key));
    }
    if (wsManager != null) {
      wsManager.disconnect();
    }
  }

  // ── Config loading ─────────────────────────────────────────────────────────

  public void loadConfigData() {
    reloadConfig();

    SERVER_NAME = getConfig().getString("server_name", "Minecraft");

    String mode = getConfig().getString("majestic.mode", "websocket");
    boolean useWebhook = "webhook".equalsIgnoreCase(mode);

    // Tear down any existing connection before rebuilding.
    if (wsManager != null) {
      wsManager.disconnect();
      wsManager = null;
    }
    webhookSender = null;

    if (useWebhook) {
      WEBHOOK_URL = getConfig().getString("majestic.webhook_url", "");
      webhookSender = new WebhookSender(this, WEBHOOK_URL, SERVER_NAME);
      getLogger()
          .info(
              "Mode: webhook (one-way Discord embeds via "
                  + (webhookSender.isConfigured() ? "configured URL" : "⚠ unconfigured URL")
                  + ")");
    } else {
      WS_URL = getConfig().getString("majestic.ws_url", "");
      API_KEY = getConfig().getString("majestic.api_key", "");
      API_SECRET = getConfig().getString("majestic.api_secret", "");

      discordChatEnabled = getConfig().getBoolean("discord_chat.enabled", true);
      discordChatFormat =
          getConfig()
              .getString("discord_chat.format", "&9[Discord] &f%discord_user%&7: &f%message%");

      loadCommandConfig();

      wsManager = new WebSocketManager(this, WS_URL, API_KEY, API_SECRET);
      wsManager.connect();
      getLogger().info("Mode: websocket (two-way bridge)");
    }

    eventEnabled.clear();
    eventColors.clear();
    eventMessages.clear();

    for (String key : EVENT_KEYS) {
      eventEnabled.put(key, getConfig().getBoolean("events." + key + ".enabled", false));
      eventColors.put(key, getConfig().getString("events." + key + ".color", "#FFFFFF"));
      eventMessages.put(key, getConfig().getString("events." + key + ".message", ""));
    }
  }

  private void loadCommandConfig() {
    commandEnabled.clear();
    commandRoleIds.clear();
    commandUserIds.clear();

    for (String cmd : COMMAND_KEYS) {
      String path = "discord_commands." + cmd;
      commandEnabled.put(cmd, getConfig().getBoolean(path + ".enabled", false));
      commandRoleIds.put(cmd, getConfig().getStringList(path + ".allowed_role_ids"));
      commandUserIds.put(cmd, getConfig().getStringList(path + ".allowed_user_ids"));
    }
  }

  // ── Event config accessors ─────────────────────────────────────────────────

  public boolean isEventEnabled(String eventKey) {
    return eventEnabled.getOrDefault(eventKey, false);
  }

  public String getEventColor(String eventKey) {
    return eventColors.getOrDefault(eventKey, "#FFFFFF");
  }

  public String getMessage(String eventKey) {
    return eventMessages.getOrDefault(eventKey, "");
  }

  // ── Outbound event dispatch ────────────────────────────────────────────────

  /**
   * Send a Minecraft event to Discord asynchronously. Routes to the active mode:
   *
   * <ul>
   *   <li><b>websocket</b> - sends a JSON frame; queued until the handshake completes.
   *   <li><b>webhook</b> - POSTs a Discord embed to the configured webhook URL.
   * </ul>
   */
  public static void sendEvent(
      String eventType, String playerName, String playerUuid, String message, String color) {
    if (instance == null) return;
    if (instance.webhookSender != null) {
      instance.webhookSender.send(eventType, playerName, playerUuid, message, color);
    } else if (instance.wsManager != null) {
      instance.wsManager.send(buildJson(eventType, playerName, playerUuid, message, color));
    }
  }

  /**
   * Send a Minecraft event synchronously. Blocks until delivery is confirmed or times out.
   * Used for server-stop so the notification is not lost during shutdown.
   */
  public static void sendEventSync(
      String eventType, String playerName, String playerUuid, String message, String color) {
    if (instance == null) return;
    if (instance.webhookSender != null) {
      instance.webhookSender.sendSync(eventType, playerName, playerUuid, message, color);
    } else if (instance.wsManager != null) {
      instance.wsManager.sendSync(buildJson(eventType, playerName, playerUuid, message, color));
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static String buildJson(
      String eventType, String playerName, String playerUuid, String message, String color) {
    return "{"
        + "\"event_type\":\""
        + esc(eventType)
        + "\","
        + "\"player_name\":\""
        + esc(playerName)
        + "\","
        + "\"player_uuid\":\""
        + esc(playerUuid)
        + "\","
        + "\"message\":\""
        + esc(message)
        + "\","
        + "\"server_name\":\""
        + esc(SERVER_NAME)
        + "\","
        + "\"color\":\""
        + esc(color)
        + "\""
        + "}";
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }
}
