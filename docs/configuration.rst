Configuration Reference
=======================

.. note::

   Configuration loading is implemented in Part 7. This page documents the
   intended schema; the actual file is not yet read at startup.

Vector uses TOML for all configuration. The main config file is
``config.toml`` in the proxy working directory.

Threading
---------

.. code-block:: toml

   [threading]
   plugin-dispatcher-threads = 0   # 0 = auto (availableProcessors * 2)
   netty-boss-threads        = 1
   netty-worker-threads      = 0   # 0 = auto

Player experience
-----------------

.. code-block:: toml

   [player-experience]
   player-count-modifier         = 1.0
   player-count-minimum          = 0
   player-count-maximum-padding  = 20
   hide-player-count             = false

   [player-experience.backend-disconnect]
   action           = "send-to-fallback"   # send-to-fallback | kick | limbo
   fallback-message = "<red>Lost connection to server..."
   limbo-timeout    = 120
   limbo-countdown  = true

Protection
----------

.. code-block:: toml

   [protection.rate-limiting]
   connections-per-second      = 10
   connections-burst           = 20
   login-attempts-per-minute   = 5

   [protection.anti-bot]
   enabled = true
   mode    = "adaptive"   # off | basic | adaptive | lockdown

   [protection.anti-bot.adaptive]
   trigger-threshold        = 20
   verification-mode        = "kick-rejoin"
   whitelist-known-players  = true

   [protection.geoip]
   enabled          = false
   database         = "./geoip/GeoLite2-Country.mmdb"
   blocked-countries = []

Management
----------

.. code-block:: toml

   [management.maintenance]
   enabled          = false
   message          = "<red>Server is under maintenance."
   allow-permission = "vector.maintenance.bypass"

   [management.forced-hosts]
   "lobby.mynetwork.net" = "lobby"
   "*.mynetwork.net"     = "lobby"

Observability
-------------

.. code-block:: toml

   [observability.metrics]
   enabled  = true
   provider = "prometheus"
   bind     = "0.0.0.0:9090"

   [observability.logging]
   format             = "pretty"   # pretty | json
   include-player-ips = false      # GDPR consideration
   log-commands       = false      # PII risk, off by default

Limbo
-----

.. code-block:: toml

   [limbo]
   unclaimed-action    = "kick"
   unclaimed-message   = "<red>Server unavailable. Try again soon."
   max-hold-duration   = 120   # seconds
   keep-alive-interval = 15    # seconds — Vector's responsibility
   keep-alive-timeout  = 30    # seconds before declaring dead

Per-server overrides
--------------------

Any top-level config key can be overridden per-server:

.. code-block:: toml

   [servers.lobby.overrides]
   "transfer.cooldown.enabled"          = false
   "player-experience.chat.filter-spam" = false
