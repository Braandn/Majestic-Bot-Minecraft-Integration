package bot.majestic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Manages the persistent WebSocket connection to the Majestic API.
 *
 * <p><b>Outbound</b>: Minecraft events are serialised to JSON and sent over the socket.
 * {@code RegisterCommands} is sent on every successful open so the Majestic bot knows which
 * Discord commands are enabled and who may invoke them.
 *
 * <p><b>Inbound</b>:
 *
 * <ul>
 *   <li>{@code DiscordMessage} - broadcast into Minecraft chat.
 *   <li>{@code DiscordCommand} - executed on the Bukkit main thread via
 *       {@link DiscordCommandExecutor}.
 * </ul>
 *
 * <p>Authentication is performed during the WebSocket handshake via the {@code X-Api-Key} and
 * {@code X-Api-Secret} HTTP headers.
 */
public class WebSocketManager {

  private static final int RECONNECT_DELAY_SECONDS = 15;
  private static final int SYNC_WAIT_MS = 5000;

  private final MajesticBot plugin;
  private final String wsUrl;
  private final String apiKey;
  private final String apiSecret;

  private volatile WebSocket webSocket;
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  /** Messages queued while the connection is being established. */
  private final Queue<String> pendingQueue = new ConcurrentLinkedQueue<>();

  private final ScheduledExecutorService scheduler;
  private final HttpClient httpClient;
  private final DiscordCommandExecutor commandExecutor;

  public WebSocketManager(MajesticBot plugin, String wsUrl, String apiKey, String apiSecret) {
    this.plugin = plugin;
    this.wsUrl = wsUrl;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.httpClient = HttpClient.newHttpClient();
    this.commandExecutor = new DiscordCommandExecutor(plugin, this);
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "majestic-ws");
              t.setDaemon(true);
              return t;
            });
  }

  /** Returns {@code true} if the config holds real (non-placeholder) credentials. */
  public boolean isConfigured() {
    return !wsUrl.isEmpty()
        && !apiKey.isEmpty()
        && !apiSecret.isEmpty()
        && !apiKey.equals("YOUR_API_KEY_HERE")
        && !apiSecret.equals("YOUR_API_SECRET_HERE");
  }

  /** Start the connection process. Logs a warning and returns early if unconfigured. */
  public void connect() {
    if (!isConfigured()) {
      plugin
          .getLogger()
          .warning(
              "WebSocket not started: API credentials are not configured."
                  + " Set majestic.api_key and majestic.api_secret in config.yml.");
      return;
    }
    plugin.getLogger().info("Connecting to WebSocket: " + wsUrl);
    doConnect();
  }

  private void doConnect() {
    if (shutdown.get()) return;
    try {
      httpClient
          .newWebSocketBuilder()
          .header("X-Api-Key", apiKey)
          .header("X-Api-Secret", apiSecret)
          .buildAsync(URI.create(wsUrl), new Listener())
          .whenComplete(
              (ws, ex) -> {
                if (ex != null) {
                  plugin.getLogger().warning("WebSocket connection failed: " + ex.getMessage());
                  scheduleReconnect();
                }
              });
    } catch (Exception e) {
      plugin.getLogger().warning("WebSocket error during connect: " + e.getMessage());
      scheduleReconnect();
    }
  }

  private void scheduleReconnect() {
    if (shutdown.get()) return;
    plugin
        .getLogger()
        .info("Reconnecting WebSocket in " + RECONNECT_DELAY_SECONDS + " seconds…");
    scheduler.schedule(this::doConnect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Send a JSON string asynchronously. If the socket is not yet open the message is queued and
   * flushed once the connection succeeds.
   */
  public void send(String json) {
    if (!isConfigured()) return;
    if (connected.get() && webSocket != null) {
      webSocket.sendText(json, true);
    } else {
      pendingQueue.offer(json);
    }
  }

  /**
   * Send a JSON string and block until delivery is confirmed (or the timeout expires). Used for
   * the server-stop event so the message is not lost on shutdown.
   */
  public void sendSync(String json) {
    if (!isConfigured()) return;
    long deadline = System.currentTimeMillis() + SYNC_WAIT_MS;
    while (!connected.get() && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    if (!connected.get() || webSocket == null) return;
    try {
      webSocket.sendText(json, true).get();
    } catch (InterruptedException | ExecutionException e) {
      plugin.getLogger().warning("Failed to send synchronous message: " + e.getMessage());
    }
  }

  /** Gracefully close the connection and stop the reconnect scheduler. */
  public void disconnect() {
    shutdown.set(true);
    connected.set(false);
    scheduler.shutdownNow();
    WebSocket ws = webSocket;
    if (ws != null) {
      try {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin disabled").get();
      } catch (InterruptedException | ExecutionException e) {
        // Ignore - we're shutting down anyway.
      }
    }
  }

  // ─── WebSocket listener ───────────────────────────────────────────────────

  private class Listener implements WebSocket.Listener {

    private final StringBuilder messageBuffer = new StringBuilder();

    @Override
    public void onOpen(WebSocket ws) {
      webSocket = ws;
      connected.set(true);
      plugin.getLogger().info("WebSocket connected.");

      // Flush messages queued before the connection opened.
      String pending;
      while ((pending = pendingQueue.poll()) != null) {
        ws.sendText(pending, true);
      }

      // Tell the Majestic bot which commands are enabled and who may use them.
      ws.sendText(plugin.buildCommandsJson(), true);

      ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      messageBuffer.append(data);
      ws.request(1);
      if (last) {
        String frame = messageBuffer.toString();
        messageBuffer.setLength(0);
        handleIncoming(frame);
      }
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      plugin.getLogger().info("WebSocket closed (" + statusCode + "): " + reason);
      if (connected.getAndSet(false)) {
        scheduleReconnect();
      }
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
      plugin.getLogger().warning("WebSocket error: " + error.getMessage());
      if (connected.getAndSet(false)) {
        scheduleReconnect();
      }
    }
  }

  // ─── Incoming message handling ────────────────────────────────────────────

  private void handleIncoming(String json) {
    String eventType = extractField(json, "event_type");
    if (eventType == null) return;

    switch (eventType) {
      case "DiscordMessage" -> handleDiscordMessage(json);
      case "DiscordCommand" -> handleDiscordCommand(json);
      default -> { /* Unknown frame types are silently ignored. */ }
    }
  }

  private void handleDiscordMessage(String json) {
    if (!plugin.isDiscordChatEnabled()) return;

    String discordUser = orEmpty(extractField(json, "discord_user"));
    String discordTag = orEmpty(extractField(json, "discord_tag"));
    String message = orEmpty(extractField(json, "message"));

    String formatted =
        plugin
            .getDiscordChatFormat()
            .replace("%discord_user%", discordUser)
            .replace("%discord_tag%", discordTag)
            .replace("%message%", message);

    Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().broadcast(component));
  }

  private void handleDiscordCommand(String json) {
    String command = orEmpty(extractField(json, "command"));
    List<String> args = extractStringArray(json, "args");
    String discordUser = orEmpty(extractField(json, "discord_user"));
    String discordUserId = orEmpty(extractField(json, "discord_user_id"));
    List<String> discordRoleIds = extractStringArray(json, "discord_role_ids");

    commandExecutor.handle(command, args, discordUser, discordUserId, discordRoleIds);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private String orEmpty(String s) {
    return s != null ? s : "";
  }

  /**
   * Minimal JSON string-field extractor. Handles escaped quotes inside string values.
   */
  private String extractField(String json, String field) {
    String key = "\"" + field + "\"";
    int keyIdx = json.indexOf(key);
    if (keyIdx == -1) return null;
    int colon = json.indexOf(':', keyIdx + key.length());
    if (colon == -1) return null;
    int open = json.indexOf('"', colon + 1);
    if (open == -1) return null;
    int close = open + 1;
    while (close < json.length()) {
      char c = json.charAt(close);
      if (c == '"' && json.charAt(close - 1) != '\\') break;
      close++;
    }
    if (close >= json.length()) return null;
    return json.substring(open + 1, close).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  /**
   * Extracts a JSON string array field (e.g. {@code "args": ["a","b"]}) as a {@link List}.
   * Returns an empty list if the field is absent or empty.
   */
  private List<String> extractStringArray(String json, String field) {
    String key = "\"" + field + "\"";
    int keyIdx = json.indexOf(key);
    if (keyIdx == -1) return Collections.emptyList();
    int colon = json.indexOf(':', keyIdx + key.length());
    if (colon == -1) return Collections.emptyList();
    int open = json.indexOf('[', colon + 1);
    if (open == -1) return Collections.emptyList();
    int close = json.indexOf(']', open + 1);
    if (close == -1) return Collections.emptyList();

    String content = json.substring(open + 1, close).trim();
    if (content.isEmpty()) return Collections.emptyList();

    List<String> result = new ArrayList<>();
    int pos = 0;
    while (pos < content.length()) {
      int openQ = content.indexOf('"', pos);
      if (openQ == -1) break;
      int closeQ = openQ + 1;
      while (closeQ < content.length()) {
        if (content.charAt(closeQ) == '"' && content.charAt(closeQ - 1) != '\\') break;
        closeQ++;
      }
      if (closeQ >= content.length()) break;
      result.add(
          content.substring(openQ + 1, closeQ).replace("\\\"", "\"").replace("\\\\", "\\"));
      pos = closeQ + 1;
    }
    return result;
  }
}
