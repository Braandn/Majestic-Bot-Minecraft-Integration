package bot.majestic.utils;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Formats message templates by replacing %variable% placeholders with their values and sanitizing
 * player-controlled input to prevent Discord mention injection.
 *
 * <p>Available variables:
 *
 * <ul>
 *   <li>%player_name% - in-game player name
 *   <li>%player_uuid% - player UUID
 *   <li>%message% - chat message text
 *   <li>%death_message% - vanilla death message
 *   <li>%advancement% - advancement display name
 *   <li>%server_name% - configured server name
 * </ul>
 */
public class MessageFormatter {

  private static final Pattern MENTION_PATTERN = Pattern.compile("<@[!&]?\\d+>");

  private static final Pattern EVERYONE_PATTERN =
      Pattern.compile("@(everyone|here)", Pattern.CASE_INSENSITIVE);

  /**
   * Sanitize a string to prevent Discord mention abuse. Strips &lt;@...&gt; pings and
   * neuters @everyone / @here.
   */
  public static String sanitize(String input) {
    if (input == null || input.isEmpty()) return "";
    String result = MENTION_PATTERN.matcher(input).replaceAll("[mention]");
    result = EVERYONE_PATTERN.matcher(result).replaceAll("@\u200b$1");
    return result;
  }

  /**
   * Replace %variable% placeholders in a template with their sanitized values from the provided
   * map.
   *
   * @param template message template from config (trusted)
   * @param variables map of variable name → raw value (untrusted player input)
   * @return the formatted, sanitized message
   */
  public static String format(String template, Map<String, String> variables) {
    if (template == null || template.isEmpty()) return "";

    String result = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      String placeholder = "%" + entry.getKey() + "%";
      if (result.contains(placeholder)) {
        String safeValue = sanitize(entry.getValue());
        result = result.replace(placeholder, safeValue);
      }
    }
    return result;
  }
}
