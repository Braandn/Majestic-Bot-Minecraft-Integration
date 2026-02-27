package bot.majestic;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MajesticBot extends JavaPlugin {

  private static String ENDPOINT = "https://xxx.xxx/api/minecraft/webhook";
  private static String API_KEY = "YOUR_API_KEY";
  private static String API_SECRET = "YOUR_SECRET";

  private static String SERVER_NAME = "";

  private final Map<String, Boolean> eventEnabled = new HashMap<>();
  private final Map<String, String> eventColors = new HashMap<>();

  private VersionChecker versionChecker;

  public VersionChecker getVersionChecker() {
    return versionChecker;
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    loadConfigData();
    PluginManager pm = Bukkit.getPluginManager();
    pm.registerEvents(new Events(this), this);
    getCommand("majestic").setExecutor(new Reload(this));

    versionChecker = new VersionChecker(this);
    versionChecker.start();
  }

  @Override
  public void onDisable() {}

  public void loadConfigData() {
    ENDPOINT = getConfig().getString("majestic.api_url");
    API_KEY = getConfig().getString("majestic.api_key");
    API_SECRET = getConfig().getString("majestic.api_secret");
    SERVER_NAME = getConfig().getString("server_name");

    eventEnabled.clear();
    eventColors.clear();

    String[] eventKeys = {"player_chat", "player_join", "player_leave", "player_death"};

    for (String key : eventKeys) {
      boolean enabled = getConfig().getBoolean("events." + key, false);
      eventEnabled.put(key, enabled);

      String color = getConfig().getString("colors." + key, "#FFFFFF");
      eventColors.put(key, color);
    }
  }

  public boolean isEventEnabled(String eventKey) {
    return eventEnabled.getOrDefault(eventKey, false);
  }

  public String getEventColor(String eventKey) {
    return eventColors.getOrDefault(eventKey, "#FFFFFF");
  }

  /**
   * Send a player event to Discord asynchronously (fire-and-forget).
   *
   * @param eventType "PlayerChat" | "PlayerJoin" | "PlayerLeave" | "PlayerDeath"
   * @param playerName In-game display name
   * @param playerUuid player.getUniqueId().toString() — pass "" if unknown
   * @param message Chat text or death message; pass "" for default join/leave event messages
   * @param color Discord embed color; pass "" for default color selection
   */
  public static void sendEvent(
      String eventType, String playerName, String playerUuid, String message, String color) {
    new Thread(
            () -> {
              try {
                String body =
                    "{"
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
                        + "\"}";

                HttpURLConnection con =
                    (HttpURLConnection) new URI(ENDPOINT).toURL().openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("X-Api-Key", API_KEY);
                con.setRequestProperty("X-Api-Secret", API_SECRET);
                con.setDoOutput(true);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                try (OutputStream os = con.getOutputStream()) {
                  os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                con.getResponseCode();
                con.disconnect();

              } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
              }
            },
            "majestic-bot-event")
        .start();
  }

  private static String esc(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
