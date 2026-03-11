package bot.majestic;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Executes Discord-originated server commands (ban, kick, whitelist, info) on the Bukkit main
 * thread and sends a {@code CommandResult} frame back over the WebSocket.
 *
 * <p>Permission checking (enabled flag + allowed role / user ID lists) is performed before
 * scheduling any Bukkit work, matching the enforcement already applied by the Majestic Discord bot.
 */
public class DiscordCommandExecutor {

  private final MajesticBot plugin;
  private final WebSocketManager wsManager;

  public DiscordCommandExecutor(MajesticBot plugin, WebSocketManager wsManager) {
    this.plugin = plugin;
    this.wsManager = wsManager;
  }

  /**
   * Entry point called from the WebSocket listener thread. Validates permissions then hands off to
   * the main thread for Bukkit API calls.
   */
  public void handle(
      String command,
      List<String> args,
      String discordUser,
      String discordUserId,
      List<String> discordRoleIds) {

    if (!plugin.isCommandEnabled(command)) {
      sendResult(command, false, "Command `" + command + "` is disabled on this server.", discordUser);
      return;
    }

    if (!plugin.isCommandAuthorized(command, discordUserId, discordRoleIds)) {
      sendResult(command, false, "You do not have permission to use `" + command + "`.", discordUser);
      return;
    }

    Bukkit.getScheduler()
        .runTask(plugin, () -> dispatch(command, args, discordUser));
  }

  // ─── Command dispatch ─────────────────────────────────────────────────────

  private void dispatch(String command, List<String> args, String discordUser) {
    switch (command.toLowerCase()) {
      case "ban"       -> executeBan(args, discordUser);
      case "kick"      -> executeKick(args, discordUser);
      case "whitelist" -> executeWhitelist(args, discordUser);
      case "info"      -> executeInfo(discordUser);
      default          -> sendResult(command, false, "Unknown command: `" + command + "`.", discordUser);
    }
  }

  // ─── ban ──────────────────────────────────────────────────────────────────

  private void executeBan(List<String> args, String discordUser) {
    if (args.isEmpty()) {
      sendResult("ban", false, "Usage: `ban <player> [reason]`", discordUser);
      return;
    }
    String playerName = args.get(0);
    String reason =
        args.size() > 1
            ? String.join(" ", args.subList(1, args.size()))
            : "Banned via Discord";

    Bukkit.getBanList(BanList.Type.NAME)
        .addBan(playerName, reason, null, "Discord: " + discordUser);

    Player online = Bukkit.getPlayerExact(playerName);
    if (online != null) {
      online.kick(Component.text(reason));
    }

    sendResult("ban", true, "**" + playerName + "** has been banned. Reason: " + reason, discordUser);
  }

  // ─── kick ─────────────────────────────────────────────────────────────────

  private void executeKick(List<String> args, String discordUser) {
    if (args.isEmpty()) {
      sendResult("kick", false, "Usage: `kick <player> [reason]`", discordUser);
      return;
    }
    String playerName = args.get(0);
    String reason =
        args.size() > 1
            ? String.join(" ", args.subList(1, args.size()))
            : "Kicked via Discord";

    Player player = Bukkit.getPlayerExact(playerName);
    if (player == null) {
      sendResult("kick", false, "Player **" + playerName + "** is not online.", discordUser);
      return;
    }

    player.kick(Component.text(reason));
    sendResult("kick", true, "**" + playerName + "** has been kicked. Reason: " + reason, discordUser);
  }

  // ─── whitelist ────────────────────────────────────────────────────────────

  private void executeWhitelist(List<String> args, String discordUser) {
    if (args.isEmpty()) {
      sendResult("whitelist", false, "Usage: `whitelist <on|off|add|remove> [player]`", discordUser);
      return;
    }
    switch (args.get(0).toLowerCase()) {
      case "on" -> {
        Bukkit.setWhitelist(true);
        sendResult("whitelist", true, "Whitelist has been **enabled**.", discordUser);
      }
      case "off" -> {
        Bukkit.setWhitelist(false);
        sendResult("whitelist", true, "Whitelist has been **disabled**.", discordUser);
      }
      case "add" -> {
        if (args.size() < 2) {
          sendResult("whitelist", false, "Usage: `whitelist add <player>`", discordUser);
          return;
        }
        String target = args.get(1);
        Bukkit.getOfflinePlayer(target).setWhitelisted(true);
        sendResult("whitelist", true, "**" + target + "** has been added to the whitelist.", discordUser);
      }
      case "remove" -> {
        if (args.size() < 2) {
          sendResult("whitelist", false, "Usage: `whitelist remove <player>`", discordUser);
          return;
        }
        String target = args.get(1);
        Bukkit.getOfflinePlayer(target).setWhitelisted(false);
        sendResult("whitelist", true, "**" + target + "** has been removed from the whitelist.", discordUser);
      }
      default ->
          sendResult("whitelist", false, "Unknown subcommand. Use: `on`, `off`, `add`, `remove`.", discordUser);
    }
  }

  // ─── info ─────────────────────────────────────────────────────────────────

  private void executeInfo(String discordUser) {
    int online = Bukkit.getOnlinePlayers().size();
    int max = Bukkit.getMaxPlayers();
    String version = Bukkit.getVersion();
    boolean whitelistOn = Bukkit.hasWhitelist();

    String tps;
    try {
      double[] tpsArr = Bukkit.getServer().getTPS();
      tps = String.format("%.2f / %.2f / %.2f (1m / 5m / 15m)", tpsArr[0], tpsArr[1], tpsArr[2]);
    } catch (NoSuchMethodError e) {
      tps = "N/A";
    }

    String msg =
        "**Players:** "
            + online
            + "/"
            + max
            + "\n**Version:** "
            + version
            + "\n**TPS:** "
            + tps
            + "\n**Whitelist:** "
            + (whitelistOn ? "enabled" : "disabled");

    sendResult("info", true, msg, discordUser);
  }

  // ─── Result sender ────────────────────────────────────────────────────────

  private void sendResult(String command, boolean success, String message, String discordUser) {
    String json =
        "{"
            + "\"event_type\":\"CommandResult\","
            + "\"command\":\""
            + esc(command)
            + "\","
            + "\"success\":"
            + success
            + ","
            + "\"message\":\""
            + esc(message)
            + "\","
            + "\"discord_user\":\""
            + esc(discordUser)
            + "\""
            + "}";
    wsManager.send(json);
  }

  private String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
}
