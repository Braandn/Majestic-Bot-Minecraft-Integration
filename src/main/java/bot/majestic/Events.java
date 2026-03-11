package bot.majestic;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import bot.majestic.utils.AdvancementNameResolver;
import bot.majestic.utils.MessageFormatter;

public class Events implements Listener {

  private final MajesticBot plugin;

  public Events(MajesticBot plugin) {
    this.plugin = plugin;
  }

  private Map<String, String> playerVars(String name, String uuid) {
    Map<String, String> vars = new HashMap<>();
    vars.put("player_name", name);
    vars.put("player_uuid", uuid);
    vars.put("server_name", MajesticBot.getServerName());
    vars.put("online_players", String.valueOf(Bukkit.getOnlinePlayers().size()));
    vars.put("max_players", String.valueOf(Bukkit.getMaxPlayers()));
    return vars;
  }

  @EventHandler
  public void onChat(AsyncPlayerChatEvent e) {
    String key = "player_chat";
    if (!plugin.isEventEnabled(key)) return;

    Map<String, String> vars =
        playerVars(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString());
    vars.put("message", e.getMessage());

    String formatted = MessageFormatter.format(plugin.getMessage(key), vars);

    MajesticBot.sendEvent(
        "PlayerChat",
        e.getPlayer().getName(),
        e.getPlayer().getUniqueId().toString(),
        formatted,
        plugin.getEventColor(key));
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    String key = "player_join";
    if (plugin.isEventEnabled(key)) {
      Map<String, String> vars =
          playerVars(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString());

      String formatted = MessageFormatter.format(plugin.getMessage(key), vars);

      MajesticBot.sendEvent(
          "PlayerJoin",
          e.getPlayer().getName(),
          e.getPlayer().getUniqueId().toString(),
          formatted,
          plugin.getEventColor(key));
    }

    if (e.getPlayer().isOp() || e.getPlayer().hasPermission("*")) {
      plugin.getVersionChecker().notifyIfOutdated(e.getPlayer());
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    String key = "player_leave";
    if (!plugin.isEventEnabled(key)) return;

    Map<String, String> vars =
        playerVars(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString());
    int actualOnline = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
    vars.put("online_players", String.valueOf(actualOnline));

    String formatted = MessageFormatter.format(plugin.getMessage(key), vars);

    MajesticBot.sendEvent(
        "PlayerLeave",
        e.getPlayer().getName(),
        e.getPlayer().getUniqueId().toString(),
        formatted,
        plugin.getEventColor(key));
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    String key = "player_death";
    if (!plugin.isEventEnabled(key)) return;

    Map<String, String> vars =
        playerVars(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString());
    vars.put("death_message", e.getDeathMessage() != null ? e.getDeathMessage() : "");

    String formatted = MessageFormatter.format(plugin.getMessage(key), vars);

    MajesticBot.sendEvent(
        "PlayerDeath",
        e.getPlayer().getName(),
        e.getPlayer().getUniqueId().toString(),
        formatted,
        plugin.getEventColor(key));
  }

  @EventHandler
  public void onAdvancement(PlayerAdvancementDoneEvent e) {
    String key = "player_advancement";
    if (!plugin.isEventEnabled(key)) return;

    if (AdvancementNameResolver.isHidden(e.getAdvancement())) return;

    String advancementName = AdvancementNameResolver.resolve(e.getAdvancement());

    Map<String, String> vars =
        playerVars(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString());
    vars.put("advancement", advancementName);

    String formatted = MessageFormatter.format(plugin.getMessage(key), vars);

    MajesticBot.sendEvent(
        "PlayerAdvancement",
        e.getPlayer().getName(),
        e.getPlayer().getUniqueId().toString(),
        formatted,
        plugin.getEventColor(key));
  }
}
