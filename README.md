![Minecraft](https://img.shields.io/badge/Minecraft-1.17.1_--_1.21.11-brightgreen)
![Java](https://img.shields.io/badge/Java-17%2F21-orange)
![Releases](https://img.shields.io/github/v/release/Braandn/Majestic-Bot-Minecraft-Integration)
![License](https://img.shields.io/github/license/Braandn/Majestic-Bot-Minecraft-Integration)

# MajesticBot - Minecraft Discord Integration Plugin

MajesticBot is a lightweight Bukkit/Spigot/Paper plugin that bridges your Minecraft server and Discord. It supports two independent modes - **WebSocket** and **Webhook** - and you only need to configure one.

---

## Which mode should I use?

| | WebSocket mode | Webhook mode |
|---|---|---|
| **Minecraft → Discord** | ✅ Rich embeds | ✅ Rich embeds |
| **Discord → Minecraft chat** | ✅ | ❌ |
| **Discord → Minecraft commands** | ✅ (`ban`, `kick`, `whitelist`, `info`) | ❌ |
| **Requires Majestic Bot** | Yes | No |
| **Setup complexity** | Medium | Easy |

**Choose WebSocket if** you want a two-way bridge - Discord messages appear in-game and Discord users can run server commands.

**Choose Webhook if** you only want Minecraft events posted to Discord and don't need any Discord → Minecraft features. No Majestic Bot account needed - just a Discord webhook URL.

---

## Installation

1. Download the JAR for your Minecraft version from the [Releases page](https://github.com/Braandn/Majestic-Bot-Minecraft-Integration/releases).
2. Drop it into your server's `plugins/` directory.
3. Start the server - it will generate `plugins/MajesticBot/config.yml`.
4. Follow the setup guide for your chosen mode below.
5. Run `/majestic reload` or restart the server.

**Requirements**: Bukkit / Spigot / Paper 1.17.1+, Java 17 or 21. No extra plugins needed.

---

## Setup - Webhook mode (easy, one-way)

Webhook mode posts Minecraft events directly to a Discord channel. No Majestic Bot required.

### Step 1 - Create a Discord webhook

1. Open your Discord server and go to the channel you want events posted in.
2. Click **Edit Channel** → **Integrations** → **Webhooks** → **New Webhook**.
3. Give it a name (e.g. `Minecraft`), then click **Copy Webhook URL**.

### Step 2 - Configure the plugin

Open `plugins/MajesticBot/config.yml` and set:

```yaml
majestic:
  mode: 'webhook'
  webhook_url: 'https://discord.com/api/webhooks/YOUR_WEBHOOK_URL'
```

That's it. Run `/majestic reload` and events will start flowing to Discord.

---

## Setup - WebSocket mode (two-way bridge)

WebSocket mode connects your server to the Majestic Bot service, enabling Discord → Minecraft chat relay and Discord slash commands.

### Step 1 - Set up Majestic Bot

1. Add [Majestic Bot](https://majestic.bot) to your Discord server ([Invite Link](https://majestic.bot/invite)).
2. Go to your Majestic dashboard and open **Minecraft Integration** for your server.
3. Generate an **API key** and **API secret** - keep these private.

### Step 2 - Configure the plugin

Open `plugins/MajesticBot/config.yml` and set:

```yaml
majestic:
  mode: 'websocket'
  ws_url: 'wss://api.majestic.bot/v1/minecraft/websocket'
  api_key: 'YOUR_API_KEY_HERE'
  api_secret: 'YOUR_API_SECRET_HERE'
```

### Step 3 - (Optional) Enable Discord commands

Discord users can run server commands from within Discord. Each command is disabled by default - enable only the ones you want:

```yaml
discord_commands:

  ban:
    enabled: true
    allowed_role_ids: ['123456789012345678']  # Discord role IDs allowed to use this
    allowed_user_ids: []                       # Discord user IDs allowed to use this

  kick:
    enabled: true
    allowed_role_ids: ['123456789012345678']
    allowed_user_ids: []

  whitelist:
    enabled: false
    allowed_role_ids: []
    allowed_user_ids: []

  info:
    enabled: true
    allowed_role_ids: []   # Empty = anyone in the Discord server can use it
    allowed_user_ids: []
```

To get a role or user ID: enable **Developer Mode** in Discord (User Settings → Advanced), then right-click the role/user and select **Copy ID**.

| Permission configuration | Behavior |
|---|---|
| `enabled: false` | Command is fully disabled and not visible in Discord |
| `enabled: true`, both lists empty | Anyone in the Discord server can use it |
| `enabled: true`, lists populated | Only matching role/user IDs can use it |

### Step 4 - (Optional) Configure Discord → Minecraft chat format

```yaml
discord_chat:
  enabled: true
  format: '&9[Discord] &f%discord_user%&7: &f%message%'
```

Supports `&` color codes (e.g. `&9` blue, `&l` bold, `&r` reset).

| Variable | Description |
|---|---|
| `%discord_user%` | Discord display name |
| `%discord_tag%` | Full tag (`username` or `user#0000`) |
| `%message%` | Message content |

---

## Event configuration

Both modes share the same event settings. You can enable/disable each event, set a hex color for its Discord embed, and customize the message text.

```yaml
events:

  server_start:
    enabled: true
    color: '#FFFFFF'
    message: 'The server is now online!'

  server_stop:
    enabled: true
    color: '#FF0000'
    message: 'The server has been stopped.'

  player_chat:
    enabled: true
    color: '#0080FF'
    message: '%message%'

  player_join:
    enabled: true
    color: '#00FF00'
    message: '%player_name% joined the server'

  player_leave:
    enabled: true
    color: '#FF0000'
    message: '%player_name% left the server'

  player_death:
    enabled: true
    color: '#757575'
    message: '%death_message%'

  player_advancement:
    enabled: true
    color: '#FF9AFF'
    message: '%player_name% has made the advancement %advancement%'
```

### Available message variables

| Variable | Description | Available in |
|---|---|---|
| `%player_name%` | Player's in-game name | All player events |
| `%player_uuid%` | Player's UUID | All player events |
| `%message%` | Chat message text | `player_chat` |
| `%death_message%` | Vanilla death message | `player_death` |
| `%advancement%` | Advancement display name | `player_advancement` |
| `%server_name%` | Configured server name | All events |
| `%online_players%` | Current online player count | All events |
| `%max_players%` | Max player slots | All events |

Player-controlled values (`%player_name%`, `%message%`, etc.) are sanitized to prevent Discord mention abuse (`@everyone`, `@here`, `<@...>`).

**Examples:**
```yaml
message: '%player_name% joined! (%online_players%/%max_players%)'
message: '%player_name% was eliminated by %death_message%'
```

---

## Full configuration reference

```yaml
# Display name shown in Discord embed footers
server_name: 'Minecraft'

majestic:
  mode: 'websocket'             # 'websocket' or 'webhook'

  # WebSocket mode credentials (ignored in webhook mode)
  ws_url: 'wss://api.majestic.bot/v1/minecraft/websocket'
  api_key: 'YOUR_API_KEY_HERE'
  api_secret: 'YOUR_API_SECRET_HERE'

  # Webhook mode URL (ignored in websocket mode)
  webhook_url: 'YOUR_WEBHOOK_URL_HERE'

# Discord → Minecraft chat relay (websocket mode only)
discord_chat:
  enabled: true
  format: '&9[Discord] &f%discord_user%&7: &f%message%'

# Discord → Minecraft commands (websocket mode only)
discord_commands:
  ban:
    enabled: false
    allowed_role_ids: []
    allowed_user_ids: []
  kick:
    enabled: false
    allowed_role_ids: []
    allowed_user_ids: []
  whitelist:
    enabled: false
    allowed_role_ids: []
    allowed_user_ids: []
  info:
    enabled: false
    allowed_role_ids: []
    allowed_user_ids: []

events:
  server_start:
    enabled: true
    color: '#FFFFFF'
    message: 'The server is now online!'
  server_stop:
    enabled: true
    color: '#FF0000'
    message: 'The server has been stopped.'
  player_chat:
    enabled: true
    color: '#0080FF'
    message: '%message%'
  player_join:
    enabled: true
    color: '#00FF00'
    message: '%player_name% joined the server'
  player_leave:
    enabled: true
    color: '#FF0000'
    message: '%player_name% left the server'
  player_death:
    enabled: true
    color: '#757575'
    message: '%death_message%'
  player_advancement:
    enabled: true
    color: '#FF9AFF'
    message: '%player_name% has made the advancement %advancement%'
```

---

## Commands & permissions

| Command | Description | Permission |
|---|---|---|
| `/majestic reload` | Reloads config and reconnects | `majestic.reload` |

---

## Features at a glance

- **Server events**: Start and stop notifications.
- **Player events**: Chat, join, leave, death, and advancements.
- **Rich embeds**: Player avatar (mc-heads.net), event color, server name in footer, timestamp - identical layout in both modes.
- **Auto-reconnect** (WebSocket): Reconnects after 15 seconds if the connection drops.
- **Message queuing** (WebSocket): Events fired before the handshake completes are queued and delivered once connected.
- **Secure handshake** (WebSocket): Credentials travel as WebSocket upgrade headers, never in message payloads.
- **Multi-version**: Minecraft 1.17.1 – 1.21.11, Java 17 / 21.
- **Lightweight**: No runtime dependencies beyond the Bukkit/Paper API.

### Advancement display names

| Platform | Resolution method | Example |
|---|---|---|
| Paper / Folia | Adventure `displayName()` | "Diamonds!" |
| Spigot 1.19+ | `getDisplay().getTitle()` | "Diamonds!" |
| Spigot 1.17–1.18 | Formatted key fallback | "Mine Diamond" |

Recipe unlocks and hidden advancements are filtered out on all platforms.

---

## Upgrading from v2.x

Delete `plugins/MajesticBot/config.yml` and restart to generate the new format, then re-enter your credentials.

Key changes from v2.x:

| v2.x | v3.x |
|---|---|
| `majestic.api_url` (HTTP) | `majestic.ws_url` (WebSocket, `wss://`) |
| *(single mode)* | `majestic.mode: 'websocket'` or `'webhook'` |
| *(not available)* | `majestic.webhook_url` |
| *(not available)* | `discord_chat` section |
| *(not available)* | `discord_commands` section |

---

## Contributing

Contributions are welcome! Open issues or pull requests for bug fixes, features, or improvements. Please follow Bukkit best practices and include tests where possible.

## License

This project is licensed under the MIT License.
