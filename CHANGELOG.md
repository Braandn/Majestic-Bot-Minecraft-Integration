# Changelog

All notable changes to MajesticBot will be documented in this file.

## [3.0.0] - 2026-03-11

### ⚠️ Breaking Changes
- **WebSocket replaces HTTP** - The plugin no longer sends one-off HTTP POST requests. It now maintains a persistent WebSocket connection to the Majestic API. Update your config accordingly (see below).
- **`majestic.api_url` renamed to `majestic.ws_url`** - The value must be a WebSocket URL (`wss://`). Delete your old `plugins/MajesticBot/config.yml` and let the plugin generate the new default, then re-enter your credentials.

### Added
- **Dual-mode architecture** - choose one delivery mode per server via `majestic.mode` in `config.yml`:
  - **`websocket`** (default) - two-way persistent `wss://` connection to the Majestic API. Requires `api_key` + `api_secret`.
  - **`webhook`** - one-way Discord embeds sent directly to a bare Discord webhook URL. Majestic Bot not required.
- **Webhook mode** (`majestic.mode: webhook`):
  - Sends Discord embeds via HTTP POST to `majestic.webhook_url`.
  - Embed layout matches the Majestic API embed output exactly: player avatar from mc-heads.net, event color, timestamp, server name in the footer.
  - `PlayerChat` → author `"playerName:"` + avatar; title (or description if > 256 chars) = message.
  - Other player events → author = display message + avatar.
  - Server events → title (or description if > 256 chars) = message; no author.
  - Runs asynchronously for normal events; blocks on server-stop to guarantee delivery.
- **Two-way WebSocket bridge** (`majestic.mode: websocket`):
  - **Minecraft → Discord**: server/player events are sent over the socket as JSON frames.
  - **Discord → Minecraft**: `DiscordMessage` frames from the API are broadcast into in-game chat.
- **Discord → Minecraft chat relay** (WebSocket mode only) - configurable via `discord_chat.format`, supports `&` color codes and variables: `%discord_user%`, `%discord_tag%`, `%message%`.
- **Discord Commands** (WebSocket mode only) - Discord users can run server commands from Discord. Each command is individually toggled and gated behind per-command `allowed_role_ids` / `allowed_user_ids` lists (Discord snowflake IDs). Commands: `ban`, `kick`, `whitelist` (`on`/`off`/`add`/`remove`), `info`. Permission lists are transmitted to the Majestic bot via a `RegisterCommands` frame on every WebSocket open (including reconnects and `/majestic reload`) so the bot enforces them on the Discord side before forwarding. The plugin also validates permissions as a second line of defence and returns a `CommandResult` frame for every invocation.
- **WebSocket authentication via handshake headers** - `X-Api-Key` and `X-Api-Secret` sent as HTTP upgrade headers.
- **Auto-reconnect** - WebSocket reconnects after a 15-second backoff on unexpected close or error.
- **Message queuing on startup** - Events fired before the handshake completes (e.g. `server_start`) are queued and flushed on `onOpen`.
- **`/majestic reload` reconnects** - also tears down and re-establishes the active connection with fresh credentials.

### Changed
- `majestic.api_url` (HTTP endpoint) → `majestic.ws_url` (WebSocket endpoint, `wss://` scheme).
- `sendEvent` / `sendEventSync` now dispatch to the active mode's sender (`WebSocketManager` or `WebhookSender`).
- `server_stop` blocks until the message is confirmed sent before the connection is closed.

### Removed
- Direct HTTP POST to the Majestic API - replaced by `WebSocketManager` (WebSocket mode) and `WebhookSender` (webhook mode).


## [2.0.0] - 2026-03-02

### ⚠️ Breaking Changes
- **Config restructured** - The `config.yml` format has changed. Delete your old `plugins/MajesticBot/config.yml` and restart the server to generate the new format, then re-enter your API credentials.

### Added
- **Server Start event** - sends a notification to Discord when the server finishes starting.
- **Server Stop event** - sends a notification to Discord when the server shuts down (sent synchronously to guarantee delivery).
- **Player Advancement event** - sends a notification when a player earns an advancement. Works on all server platforms (Paper, Spigot, CraftBukkit). The advancement display name is resolved automatically using the best method available for your server:
  - Paper/Folia → uses Adventure `displayName()` for full localized names
  - Spigot 1.19+ → uses Bukkit `getDisplay().getTitle()`
  - Older Spigot → falls back to a formatted version of the advancement key
- **Custom messages** - every event now supports a configurable message template with variable placeholders:
  - `%player_name%` - player's in-game name
  - `%player_uuid%` - player's UUID
  - `%message%` - chat message text (player_chat)
  - `%death_message%` - vanilla death message (player_death)
  - `%advancement%` - advancement display name (player_advancement)
  - `%server_name%` - configured server name
- **Discord mention sanitization** - player-controlled variable values are sanitized to prevent `@everyone`, `@here`, and `<@...>` injection from in-game chat.
- **Per-event configuration** - each event is now grouped with its own `enabled`, `color`, and `message` fields for clearer configuration.

### Changed
- **Config format** - events, colors, and messages are now grouped per-event instead of in separate top-level sections.

### Fixed
- **Webhook spam with default credentials** - The plugin no longer attempts to send HTTP requests when the API key or secret are still set to placeholder values (`YOUR_API_KEY_HERE` / `YOUR_API_SECRET_HERE`) or are empty.


## [1.0.2] - 2026-02-28

### Changed
- Updated default `api_url` in `config.yml` to the current production Majestic Bot endpoint (fixes webhook delivery after API changes or deployment updates).


## [1.0.1] - 2026-02-27

### Added
- **VersionChecker** - Automatic update checker that notifies server operators in-game and in console when a new version is available.

### Fixed
- **Reload command not working** - In v1.0.0 the `/majestic reload` command was registered but did not work; now fully implemented.


## [1.0.0] - 2026-02-26
- Player chat, join, leave, and death events.
- Configurable webhook endpoint with API key/secret authentication.
- Per-event hex color configuration.
- `/majestic reload` command.
- Multi-version builds (1.17.1 – 1.21.11) via GitHub Actions.