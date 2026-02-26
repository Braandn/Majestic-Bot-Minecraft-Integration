package bot.majestic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Reload implements CommandExecutor {

  private final MajesticBot plugin;

  public Reload(MajesticBot plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("majestic")) return false;

    if (!sender.hasPermission("majestic.reload")) {
      sender.sendMessage(
          Component.text(
              "You do not have permission to perform this command.", NamedTextColor.RED));
      return true;
    }

    if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
      sender.sendMessage(Component.text("Usage: /majestic reload", NamedTextColor.RED));
      return true;
    }

    this.plugin.loadConfigData();
    sender.sendMessage(
        Component.text("Successfully reloaded the configuration!", NamedTextColor.GREEN));

    return true;
  }
}
