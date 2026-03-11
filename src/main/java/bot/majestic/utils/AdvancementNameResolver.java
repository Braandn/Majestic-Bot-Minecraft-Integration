package bot.majestic.utils;

import org.bukkit.advancement.Advancement;

/**
 * Resolves a human-readable name from an {@link Advancement} across all server implementations and
 * Minecraft versions.
 *
 * <p>Resolution order (first success wins):
 *
 * <ol>
 *   <li><b>Paper</b> - {@code Advancement.displayName()} → Adventure Component → {@code
 *       PlainTextComponentSerializer}. Available on Paper/Folia 1.17+.
 *   <li><b>Spigot 1.19+</b> - {@code Advancement.getDisplay().getTitle()}. Part of the Bukkit API
 *       from ~1.19 onwards. Note: this method is <em>broken on Paper</em> (PaperMC/Paper#8305),
 *       which is why the Paper path is tried first.
 *   <li><b>Fallback</b> - The advancement's {@code NamespacedKey} is formatted into a readable
 *       string (e.g. {@code minecraft:story/mine_diamond → Mine Diamond}).
 * </ol>
 *
 * <p>All resolution is done via reflection so the plugin compiles against the Paper API but runs
 * safely on any Bukkit-based server without {@link NoClassDefFoundError} or {@link
 * NoSuchMethodError}.
 */
public final class AdvancementNameResolver {

  private static Strategy strategy;

  private AdvancementNameResolver() {}

  /** Functional interface for the chosen resolution strategy. */
  @FunctionalInterface
  private interface Strategy {
    String resolve(Advancement advancement);
  }

  /**
   * Detect the best available strategy once at first invocation, then cache it for all subsequent
   * calls.
   */
  private static Strategy detectStrategy() {
    // 1) Try Paper: PlainTextComponentSerializer + Advancement.displayName()
    try {
      Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
      Advancement.class.getMethod("displayName");
      return new PaperStrategy();
    } catch (ClassNotFoundException | NoSuchMethodException | NoClassDefFoundError ignored) {
    }

    // 2) Try Spigot 1.19+: Advancement.getDisplay() → AdvancementDisplay.getTitle()
    try {
      Advancement.class.getMethod("getDisplay");
      Class.forName("org.bukkit.advancement.AdvancementDisplay").getMethod("getTitle");
      return new SpigotDisplayStrategy();
    } catch (ClassNotFoundException | NoSuchMethodException | NoClassDefFoundError ignored) {
    }

    // 3) Fallback: format the key
    return new KeyFallbackStrategy();
  }

  /**
   * Returns a human-readable name for the given advancement. Never returns {@code null}; at worst
   * returns the raw key string.
   */
  public static String resolve(Advancement advancement) {
    if (strategy == null) {
      strategy = detectStrategy();
    }
    try {
      String result = strategy.resolve(advancement);
      if (result != null && !result.isEmpty()) {
        return result;
      }
    } catch (Exception ignored) {
    }
    return formatKey(advancement);
  }

  /**
   * Check if the advancement is a recipe unlock or other hidden advancement that shouldn't be
   * announced.
   */
  public static boolean isHidden(Advancement advancement) {
    String key = advancement.getKey().getKey();
    // Recipe unlocks live under "recipes/" in all versions
    if (key.startsWith("recipes/")) {
      return true;
    }
    // Try to check getDisplay() == null (hidden advancements)
    try {
      java.lang.reflect.Method getDisplay = Advancement.class.getMethod("getDisplay");
      Object display = getDisplay.invoke(advancement);
      if (display == null) {
        return true;
      }
    } catch (Exception ignored) {
      // Method doesn't exist on this version; rely on key-based filtering only
    }
    return false;
  }

  // ── Strategy implementations ──────────────────────────────────────

  /** Paper: Advancement.displayName() → PlainTextComponentSerializer */
  private static class PaperStrategy implements Strategy {
    private final java.lang.reflect.Method displayNameMethod;
    private final Object serializer;
    private final java.lang.reflect.Method serializeMethod;

    PaperStrategy() {
      try {
        displayNameMethod = Advancement.class.getMethod("displayName");

        Class<?> ptsClass =
            Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
        java.lang.reflect.Method plainText = ptsClass.getMethod("plainText");
        serializer = plainText.invoke(null);

        Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
        serializeMethod = ptsClass.getMethod("serialize", componentClass);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String resolve(Advancement advancement) {
      try {
        Object component = displayNameMethod.invoke(advancement);
        if (component == null) return null;
        return (String) serializeMethod.invoke(serializer, component);
      } catch (Exception e) {
        return null;
      }
    }
  }

  /** Spigot 1.19+: Advancement.getDisplay().getTitle() */
  private static class SpigotDisplayStrategy implements Strategy {
    private final java.lang.reflect.Method getDisplayMethod;
    private final java.lang.reflect.Method getTitleMethod;

    SpigotDisplayStrategy() {
      try {
        getDisplayMethod = Advancement.class.getMethod("getDisplay");
        Class<?> advDisplayClass = Class.forName("org.bukkit.advancement.AdvancementDisplay");
        getTitleMethod = advDisplayClass.getMethod("getTitle");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String resolve(Advancement advancement) {
      try {
        Object display = getDisplayMethod.invoke(advancement);
        if (display == null) return null;
        return (String) getTitleMethod.invoke(display);
      } catch (Exception e) {
        return null;
      }
    }
  }

  /** Fallback: format the NamespacedKey into a readable string. */
  private static class KeyFallbackStrategy implements Strategy {
    @Override
    public String resolve(Advancement advancement) {
      return formatKey(advancement);
    }
  }

  // ── Utilities ─────────────────────────────────────────────────────

  /** Formats {@code minecraft:story/mine_diamond} → {@code Mine Diamond}. */
  static String formatKey(Advancement advancement) {
    String key = advancement.getKey().getKey(); // e.g. "story/mine_diamond"
    // Take the last path segment
    int slash = key.lastIndexOf('/');
    String raw = (slash >= 0) ? key.substring(slash + 1) : key;
    // Replace underscores with spaces and capitalize each word
    String[] words = raw.split("_");
    StringBuilder sb = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) sb.append(word.substring(1));
    }
    return sb.toString();
  }
}
