package bot.majestic;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Sends Discord embeds directly to a bare Discord webhook URL (one-way, no Majestic API
 * involvement). The embed layout mirrors the Python reference implementation exactly:
 *
 * <ul>
 *   <li>PlayerChat - author = "playerName:", icon = mc-heads avatar; title/description = message
 *   <li>Other player events - author = display message (≤256 chars), icon = mc-heads avatar
 *   <li>Server events - title or description = message (no author)
 *   <li>Footer = server name; color = per-event hex color; timestamp = current UTC time
 * </ul>
 */
public class WebhookSender {

  private static final String HEAD_BASE_URL = "https://mc-heads.net/head/";

  private final MajesticBot plugin;
  private final String webhookUrl;
  private final String serverName;

  public WebhookSender(MajesticBot plugin, String webhookUrl, String serverName) {
    this.plugin = plugin;
    this.webhookUrl = webhookUrl;
    this.serverName = serverName;
  }

  public boolean isConfigured() {
    return !webhookUrl.isEmpty() && !webhookUrl.equals("YOUR_WEBHOOK_URL_HERE");
  }

  /** Fire-and-forget delivery on a background thread. */
  public void send(
      String eventType, String playerName, String playerUuid, String message, String color) {
    if (!isConfigured()) return;
    String json = buildPayload(eventType, playerName, playerUuid, message, color);
    new Thread(() -> doPost(json), "majestic-webhook").start();
  }

  /** Blocking delivery - used for server-stop to guarantee the message is sent before shutdown. */
  public void sendSync(
      String eventType, String playerName, String playerUuid, String message, String color) {
    if (!isConfigured()) return;
    doPost(buildPayload(eventType, playerName, playerUuid, message, color));
  }

  // ─── Embed builder ────────────────────────────────────────────────────────

  private String buildPayload(
      String eventType, String playerName, String playerUuid, String message, String color) {
    int colorInt = hexToInt(color);
    String timestamp = Instant.now().toString();
    boolean isPlayerEvent =
        eventType.startsWith("Player") && playerUuid != null && !playerUuid.isEmpty();

    StringBuilder embed = new StringBuilder();
    embed.append("{");
    embed.append("\"color\":").append(colorInt).append(",");
    embed.append("\"timestamp\":\"").append(timestamp).append("\"");

    if (isPlayerEvent) {
      if ("PlayerChat".equals(eventType)) {
        // author = "playerName:", avatar; title or description = message text
        embed
            .append(",\"author\":{")
            .append("\"name\":\"")
            .append(esc(playerName))
            .append(":\",")
            .append("\"icon_url\":\"")
            .append(HEAD_BASE_URL)
            .append(esc(playerUuid))
            .append("\"")
            .append("}");
        if (message.length() <= 256) {
          embed.append(",\"title\":\"").append(esc(message)).append("\"");
        } else {
          embed.append(",\"description\":\"").append(esc(message)).append("\"");
        }
      } else {
        // author = full display message (capped at 256), avatar; no separate title
        String authorName = message.length() > 256 ? message.substring(0, 256) : message;
        embed
            .append(",\"author\":{")
            .append("\"name\":\"")
            .append(esc(authorName))
            .append("\",")
            .append("\"icon_url\":\"")
            .append(HEAD_BASE_URL)
            .append(esc(playerUuid))
            .append("\"")
            .append("}");
      }
    } else {
      // Server event - no player avatar
      if (message.length() <= 256) {
        embed.append(",\"title\":\"").append(esc(message)).append("\"");
      } else {
        embed.append(",\"description\":\"").append(esc(message)).append("\"");
      }
    }

    if (!serverName.isEmpty()) {
      embed.append(",\"footer\":{\"text\":\"").append(esc(serverName)).append("\"}");
    }

    embed.append("}");
    return "{\"embeds\":[" + embed + "]}";
  }

  // ─── HTTP ─────────────────────────────────────────────────────────────────

  private void doPost(String json) {
    try {
      HttpURLConnection con =
          (HttpURLConnection) new URI(webhookUrl).toURL().openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json");
      con.setDoOutput(true);
      con.setConnectTimeout(5000);
      con.setReadTimeout(5000);
      try (OutputStream os = con.getOutputStream()) {
        os.write(json.getBytes(StandardCharsets.UTF_8));
      }
      int status = con.getResponseCode();
      if (status < 200 || status >= 300) {
        plugin.getLogger().warning("Webhook returned HTTP " + status);
      }
      con.disconnect();
    } catch (IOException | URISyntaxException e) {
      plugin.getLogger().warning("Webhook delivery failed: " + e.getMessage());
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private int hexToInt(String hex) {
    if (hex == null || hex.isEmpty()) return 0xFFFFFF;
    try {
      return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
    } catch (NumberFormatException e) {
      return 0xFFFFFF;
    }
  }

  private String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
}
