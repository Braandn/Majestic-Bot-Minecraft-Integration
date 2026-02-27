package bot.majestic;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Events implements Listener {

  private final MajesticBot plugin;

  public Events(MajesticBot plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onChat(AsyncPlayerChatEvent e) {
    String key = "player_chat";
    if (plugin.isEventEnabled(key)) {
      MajesticBot.sendEvent(
          "PlayerChat",
          e.getPlayer().getName(),
          e.getPlayer().getUniqueId().toString(),
          e.getMessage(),
          plugin.getEventColor(key));
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    String key = "player_join";
    if (plugin.isEventEnabled(key)) {
      MajesticBot.sendEvent(
          "PlayerJoin",
          e.getPlayer().getName(),
          e.getPlayer().getUniqueId().toString(),
          "",
          plugin.getEventColor(key));
    }

    if (e.getPlayer().isOp() || e.getPlayer().hasPermission("*")) {
      plugin.getVersionChecker().notifyIfOutdated(e.getPlayer());
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    String key = "player_leave";
    if (plugin.isEventEnabled(key)) {
      MajesticBot.sendEvent(
          "PlayerLeave",
          e.getPlayer().getName(),
          e.getPlayer().getUniqueId().toString(),
          "",
          plugin.getEventColor(key));
    }
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    String key = "player_death";
    if (plugin.isEventEnabled(key)) {
      MajesticBot.sendEvent(
          "PlayerDeath",
          e.getPlayer().getName(),
          e.getPlayer().getUniqueId().toString(),
          e.getDeathMessage() != null ? e.getDeathMessage() : "",
          plugin.getEventColor(key));
    }
  }
}
