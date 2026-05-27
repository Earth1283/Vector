Configuration Reference
=======================

Vector uses TOML for all configuration. On first start the proxy writes a
default ``vector.toml`` to the working directory if the file does not exist.

Current Implemented Config
--------------------------

This is the full schema **active today** (Parts 1–7.7). The file below is
what Vector writes to ``vector.toml`` on first start — every option is
commented in place so the file is self-documenting. You do not need this
page to configure the proxy; it exists for quick reference.

.. code-block:: toml

   # Address the proxy binds to. Format: "host:port"
   # Use 0.0.0.0 to listen on all interfaces, or a specific IP to restrict.
   bind = "0.0.0.0:25565"
   # bind = "192.168.1.10:25565"   # bind to a specific NIC only

   # Backend servers the proxy can route players to. Format: name = "host:port"
   # Add as many entries as you need; names are referenced in [routing] and
   # [management.forced-hosts]. Each server must run in offline mode.
   [servers]
   lobby    = "localhost:25577"
   # survival  = "localhost:25578"
   # creative  = "10.0.0.5:25577"
   # minigames = "minigames.internal:25565"

   # Controls which server(s) a player is sent to on first connect.
   # Entries are tried in order; the first one that accepts the connection wins.
   # All names must match keys in [servers].
   [routing]
   try = ["lobby"]
   # try = ["lobby", "survival"]   # fall back to survival if lobby is down

   # How the proxy passes the real player identity (IP, UUID, skin) to backends.
   # Backends receive connections from the proxy IP, not the player's, so this
   # is required for plugins that need the real player IP or UUID.
   #
   #   none        - No forwarding. Backend sees proxy IP only. Safe only when
   #                 backends are firewalled from the public internet.
   #   legacy      - BungeeCord-style. Injects player IP, UUID, and skin JSON
   #                 into the Handshake serverAddress field (null-byte separated).
   #                 Requires bungeecord: true in spigot.yml on each backend.
   #   bungeeguard - Same as legacy, plus a signed HMAC token appended as a fake
   #                 profile property. Prevents forged direct connections.
   #                 Requires the BungeeGuard plugin on each backend.
   #   modern      - Velocity native forwarding. Proxy signs a LoginPluginMessage
   #                 on the velocity:player_info channel with HMAC-SHA256.
   #                 Requires velocity-native-forwarding: true in paper.yml.
   #                 Recommended for Paper/Purpur backends.
   #
   # secret is required for bungeeguard and modern; ignored for none/legacy.
   [forwarding]
   mode   = "none"
   secret = ""
   # mode   = "modern"
   # secret = "change-me-to-a-long-random-string"

   # Packet compression between proxy and clients.
   # threshold - minimum packet size in bytes before compression is applied.
   #             Lower values compress more but use more CPU. -1 disables entirely.
   [compression]
   threshold = 256
   # threshold = 512   # less aggressive; good for high-CPU environments
   # threshold = -1    # disable compression entirely (e.g. LAN-only deployments)

   # Shared SQLite database used by the proxy and plugins for persistent storage.
   # The file is created automatically on first start. The directory must exist.
   [storage]
   file = "data/vector.db"
   # file = "/var/lib/vector/vector.db"   # absolute path on Linux servers

   # Server-list ping response shown in the Minecraft multiplayer screen.
   #
   # description - plain text or Adventure JSON component (starts with {).
   # favicon     - path to a 64x64 PNG. Leave the file absent to show no icon.
   [motd]
   description = "A Vector Proxy"
   # description = "Welcome to My Network!"
   # description = "{\"text\":\"My \",\"extra\":[{\"text\":\"Network\",\"color\":\"gold\",\"bold\":true}]}"
   favicon     = "server-icon.png"

   # Netty I/O thread pool sizes.
   # boss-threads   - threads that accept new TCP connections. 1 is almost always
   #                  enough regardless of player count.
   # worker-threads - threads that handle I/O for connected channels (packet
   #                  encode/decode, state transitions). 0 = auto, which sets it
   #                  to availableProcessors * 2. Increase only if profiling shows
   #                  I/O saturation.
   [threading]
   boss-threads   = 1
   worker-threads = 0
   # worker-threads = 8   # pin to a fixed count on machines with many cores

   # Maintenance mode: when enabled, connecting players are disconnected
   # immediately with the configured message before any authentication occurs.
   # message supports plain text or Adventure JSON (same format as motd.description).
   [management.maintenance]
   enabled = false
   message = "Server is under maintenance."
   # message = "{\"text\":\"We'll be back soon!\",\"color\":\"red\"}"

   # Forced-host routing: map a hostname to a backend server name.
   # When a player connects via a matching hostname (as seen in the Handshake
   # packet) they are routed directly to that server, bypassing routing.try.
   # Uncomment the section header and add entries to enable.
   #
   # [management.forced-hosts]
   # "lobby.example.com"    = "lobby"
   # "survival.example.com" = "survival"
   # "creative.example.com" = "creative"

   # Controls what players see in the server list and what happens when a
   # backend server drops their connection mid-session.
   [player-experience]
   # Multiplies the raw online player count shown in the server list.
   # 1.0 = show real count. 0.0 = always show 0. 2.0 = double the displayed count.
   player-count-modifier        = 1.0

   # Floor for the displayed online count. The final value is never shown below this.
   player-count-minimum         = 0
   # player-count-minimum = 10   # always show at least 10 players online

   # Added to the displayed maximum player count. Use to make the server look
   # less full (e.g. padding = 10 shows max as realMax + 10).
   player-count-maximum-padding = 0
   # player-count-maximum-padding = 20   # show 20 extra slots as headroom

   # Replace the online/max numbers with ??? in the server list.
   # Overrides modifier, minimum, and padding when true.
   hide-player-count            = false

   # What to do when the backend server disconnects a player during play.
   #
   #   kick             - disconnect the player from the proxy immediately.
   #   send-to-fallback - attempt to move the player to the next server in
   #                      routing.try before kicking.
   #
   # fallback-message is shown to the player if no fallback server is available
   # or if action = "kick".
   [player-experience.backend-disconnect]
   action           = "kick"
   fallback-message = "Lost connection to server."
   # action           = "send-to-fallback"
   # fallback-message = "The server you were on went down. Sending you to the lobby..."

   # Limbo holds authenticated players when no backend server is reachable.
   #
   #   unclaimed-action  - what to do when authentication succeeds but no backend
   #                       server in routing.try is available.
   #
   #     kick - disconnect immediately with unclaimed-message.
   #     hold - suspend the player for up to max-hold-duration seconds, retrying
   #            every 5 s until a backend becomes available.
   #
   #   unclaimed-message  - shown to held players when max-hold-duration expires,
   #                        or immediately when action = "kick".
   #   max-hold-duration  - seconds before a held player is kicked. 0 = unlimited.
   [limbo]
   unclaimed-action    = "kick"
   unclaimed-message   = "No server available."
   max-hold-duration   = 120
   # unclaimed-action  = "hold"
   # max-hold-duration = 0   # hold indefinitely until a server comes up

Forwarding Modes
~~~~~~~~~~~~~~~~

The proxy sits between the client and backend. Because the backend receives a
connection from the proxy's IP (not the player's), forwarding modes exist to
pass the real player IP, UUID, and skin properties to the backend.

.. mermaid::

   flowchart TD
       None["mode = none\nNo identity forwarding.\nBackend sees proxy IP only.\nOK for private setups where\nthe backend trusts the proxy."]
       Legacy["mode = legacy\nBungeeCord-style.\nInjects player IP, UUID, and\nskin JSON into the Handshake\nserverAddress field separated\nby null bytes.\nBackend must have\nbungeecord: true in spigot.yml"]
       BG["mode = bungeeguard\nSame as legacy + appends\na HMAC token as a fake\nprofile property.\nPrevents forged connections.\nRequires BungeeGuard plugin\non backend."]
       Modern["mode = modern\nVelocity native forwarding.\nProxy responds to backend's\nLoginPluginMessage on\nvelocity:player_info channel\nwith HMAC-SHA256 signed payload.\nRequires velocity-native-forwarding\nin paper.yml / purpur.yml"]

Choose **modern** for Paper-based backends. Use **bungeeguard** for Spigot/Bukkit
backends. Use **none** only if the backend is completely firewalled from the
internet.

Planned Config (Part 7.8+)
--------------------------

The sections below are designed but not yet implemented.

Threading — Plugin Dispatcher
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A dedicated ``plugin-dispatcher-threads`` key is planned once the plugin
dispatcher moves to a named thread pool instead of ``Dispatchers.Default``:

.. code-block:: toml

   [threading]
   plugin-dispatcher-threads = 0   # 0 = auto (availableProcessors × 2)
   boss-threads              = 1   # already active
   worker-threads            = 0   # already active

.. mermaid::

   flowchart LR
       BossGroup["Netty boss group\n1 thread\nAccepts new TCP connections\nHands off to worker"]
       WorkerGroup["Netty worker group\nauto threads\nI/O read/write per channel\nPacket decode/encode\nState transitions"]
       PluginDispatcher["Plugin dispatcher\nauto threads\nEvent handler coroutines\nScheduled tasks\nDatabase I/O"]

       BossGroup --> WorkerGroup
       WorkerGroup -->|"proxyScope.launch { }"| PluginDispatcher

Protection
~~~~~~~~~~

.. code-block:: toml

   [protection.rate-limiting]
   connections-per-second    = 10
   connections-burst         = 20
   login-attempts-per-minute = 5

   [protection.anti-bot]
   enabled = true
   mode    = "adaptive"   # off | basic | adaptive | lockdown

   [protection.anti-bot.adaptive]
   trigger-threshold       = 20     # joins/s before switching to verification
   verification-mode       = "kick-rejoin"
   whitelist-known-players = true

   [protection.geoip]
   enabled           = false
   database          = "./geoip/GeoLite2-Country.mmdb"
   blocked-countries = []

Anti-bot mode progression:

.. mermaid::

   flowchart LR
       Off[off\nNo checks] --> Basic
       Basic[basic\nRate limit only] --> Adaptive
       Adaptive[adaptive\nSwitches verification mode\nwhen join rate spikes] --> Lockdown
       Lockdown[lockdown\nWhitelist only\nManual reset required]

       Adaptive -->|"joins/s > trigger-threshold"| Lockdown
       Adaptive -->|"rate normalises"| Basic

Management
~~~~~~~~~~

.. code-block:: toml

   [management.maintenance]
   enabled          = false
   message          = "<red>Server is under maintenance."
   allow-permission = "vector.maintenance.bypass"

   [management.forced-hosts]
   "lobby.mynetwork.net" = "lobby"
   "*.mynetwork.net"     = "lobby"

Forced hosts let players connect via a subdomain and be routed to a specific
server regardless of the ``routing.try`` list. The proxy reads the ``serverAddress``
field from the Handshake packet which contains the hostname the client used.

Observability
~~~~~~~~~~~~~

.. code-block:: toml

   [observability.metrics]
   enabled  = true
   provider = "prometheus"
   bind     = "0.0.0.0:9090"

   [observability.logging]
   format             = "pretty"   # pretty | json
   include-player-ips = false      # set false for GDPR compliance
   log-commands       = false      # true only for auditing (PII risk)


Per-server Overrides
~~~~~~~~~~~~~~~~~~~~

Any top-level config key can be overridden per server using dot-notation:

.. code-block:: toml

   [servers.lobby.overrides]
   "transfer.cooldown.enabled"          = false
   "player-experience.chat.filter-spam" = false

Config Loading Flow
-------------------

.. mermaid::

   flowchart TD
       Start([VectorServer init])
       Exists{vector.toml\nexists?}
       WriteDefault[Write default file\nto working directory]
       Parse[Parse TOML with ktoml\ndeserialise to VectorConfig]
       Validate[Validate:\n• bind address format\n• server addresses\n• forwarding secret present\n  if mode ≠ none]
       Apply[Apply:\n• Parse server map\n• Generate RSA key pair\n• Init event bus\n• Init plugin manager]

       Start --> Exists
       Exists -->|no| WriteDefault --> Parse
       Exists -->|yes| Parse
       Parse --> Validate --> Apply
