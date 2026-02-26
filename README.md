![Minecraft](https://img.shields.io/badge/Minecraft-1.17.1_--_1.21.11-brightgreen)
![Java](https://img.shields.io/badge/Java-17%2F21-orange)
![Releases](https://img.shields.io/github/v/release/Braandn/Majestic-Bot-Minecraft-Integration)
![License](https://img.shields.io/github/license/Braandn/Majestic-Bot-Minecraft-Integration)

# MajesticBot - Minecraft Discord Integration Plugin
MajesticBot is a lightweight Bukkit/Spigot/Paper plugin that integrates your Minecraft server with Discord by sending real-time player events (such as chat messages, joins, leaves, and deaths) to a configured webhook endpoint. 
It uses asynchronous HTTP requests to ensure minimal impact on server performance. 
The plugin is designed for easy setup and customization, with support for multiple Minecraft versions via GitHub Actions builds.

## Features

- **Event Handling**: Automatically captures and forwards the following player events to Discord:
  - Player chat messages
  - Player joins
  - Player leaves
  - Player deaths (including death messages)
- **Configurable Webhook**: Send events to a custom API endpoint (e.g., https://majestic.bot/api/minecraft/webhook) with API key and secret for authentication.
- **Custom Colors**: Define Discord embed colors for each event type in the config.
- **Server Naming**: Include a custom server name in event payloads for multi-server setups.
- **Reload Command**: Use `/majestic reload` to reload the configuration without restarting the server.
  **Required permission**: `majestic.reload`
- **Asynchronous Sending**: Events are sent in a separate thread to avoid blocking the main server thread.
- **Multi-Version Support**: Built for Minecraft versions 1.17.1 to 1.21.11 (and compatible snapshots) using Java 17/21.
- **Lightweight**: Minimal dependencies; relies on Bukkit API.

## Installation

1. **Download the Plugin**:
   - Grab the latest release from the [Releases page](https://github.com/Braandn/Majestic-Bot-Minecraft-Integration/releases). Choose the JAR file matching your Minecraft version.

2. **Place in Plugins Folder**:
   - Drop the JAR file into your server's `plugins/` directory.

3. **Configure**:
   - Start your server to generate the default `config.yml` in `plugins/MajesticBot/`.
   - Edit the config with your API details (see Configuration section below).

4. **Restart or Reload**:
   - Restart the server or use `/majestic reload` to apply changes.

**Dependencies**:
- Bukkit, Spigot, or PaperMC server (tested on versions 1.17.1+).
- Java 17 or 21 (depending on the Minecraft version).

No additional plugins are required.

## Configuration

The plugin uses a simple `config.yml` file for setup. Here's the default configuration:

```yaml
server_name: 'Minecraft'  # Custom name for your server (included in event payloads)

majestic:
  api_url: 'https://majestic.bot/api/minecraft/webhook'  # Webhook endpoint URL
  api_key: 'YOUR_API_KEY_HERE'  # Your API key for authentication
  api_secret: 'YOUR_API_SECRET_HERE'  # Your API secret for authentication

events: # Which in-game events should be sent to Discord?
  player_chat: true
  player_join: true
  player_leave: true
  player_death: true

colors:  # Hex colors for Discord embeds (optional; defaults used if empty)
  player_chat: '#0080FF'
  player_join: '#00FF00'
  player_leave: '#FF0000'
  player_death: '#757575'
```
Generate your API credentials at:
https://majestic.bot/server/YOUR_DISCORD_SERVER_ID/minecraft

## Contributing
Contributions are welcome! Feel free to open issues or pull requests for bug fixes, features, or improvements. 
Please ensure code follows Bukkit best practices and includes tests where possible.

## License
This project is licensed under the MIT License
