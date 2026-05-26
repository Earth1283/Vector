Kotlin DSL — Plugin Development Guide
=======================================

Vector's native plugin API is a Kotlin DSL that gives each plugin a coroutine
scope, a typed event bus, and clean lifecycle hooks with zero boilerplate.

Quick Start
-----------

.. code-block:: kotlin

   class MyPlugin : VectorPlugin({

       onEnable {
           logger.info("Plugin ready! Proxy v{}", server.version)
       }

       onDisable {
           logger.info("Plugin shutting down.")
       }

       on<PlayerJoinEvent> { event ->
           logger.info("{} joined", event.player.username)
       }

       on<PlayerLeaveEvent> { event ->
           logger.info("{} left", event.player.username)
       }
   })

Drop the compiled JAR into ``plugins/`` alongside a ``vector-plugin.toml`` manifest
and Vector loads it at startup.

Module Dependency
-----------------

.. code-block:: kotlin

   // build.gradle.kts
   dependencies {
       compileOnly(project(":vector-api-kotlin"))   // provided at runtime by the proxy
   }

The proxy puts ``vector-api`` and ``vector-api-kotlin`` on the classpath at runtime.
Mark them ``compileOnly`` — never bundle them into your plugin JAR.

Plugin Manifest
---------------

.. code-block:: toml

   # src/main/resources/vector-plugin.toml
   id          = "my-plugin"
   name        = "My Plugin"
   version     = "1.0.0"
   api-version = "1.0"
   entrypoint  = "com.example.MyPlugin"
   language    = "KOTLIN"

   # Optional dependency declarations
   hard-deps   = ["some-required-plugin"]   # proxy refuses to start if missing
   soft-deps   = ["some-optional-plugin"]   # loaded before this one if present

Plugin Lifecycle
----------------

.. mermaid::

   flowchart LR
       Start([Proxy starts]) --> Scan[Scan plugins/ for JARs]
       Scan --> Parse[Parse vector-plugin.toml]
       Parse --> Wave[Compute load waves\nfrom hard-dep graph]
       Wave --> Parallel["Instantiate plugins\nin parallel per wave\n(Dispatchers.Default)"]
       Parallel --> Enable["enable() — runs your\nVectorPlugin { } block"]
       Enable --> Fire[fire ProxyInitializeEvent\nonEnable handlers run]
       Fire --> Accept([Accept connections])
       Accept --> Events[PlayerJoinEvent\nPlayerLeaveEvent\n...]
       Events --> Stop([proxy stop])
       Stop --> Disable["disable() — onDisable\nhandlers run in reverse order"]
       Disable --> Cleanup[event handlers unregistered\ncommands removed\ncoroutine scope cancelled]

The ``VectorPlugin { }`` constructor argument is a ``VectorPluginScope.() -> Unit``
lambda. It runs during ``enable()``, not at class construction, which means your
handlers are registered against a fully initialised proxy.

VectorPluginScope
-----------------

Every handler block runs as a coroutine **extension on** ``VectorPluginScope``.
These properties are always in scope:

.. list-table::
   :header-rows: 1
   :widths: 20 30 50

   * - Property
     - Type
     - Description
   * - ``server``
     - ``ProxyServer``
     - Proxy singleton — players, eventBus, version
   * - ``logger``
     - ``Logger`` (SLF4J)
     - Named logger for this plugin
   * - ``coroutineContext``
     - ``CoroutineContext``
     - ``SupervisorJob + Dispatchers.Default``

Because ``VectorPluginScope`` implements ``CoroutineScope``, you can call ``launch``,
``async``, ``delay``, and friends directly in any handler.

Event Handlers
--------------

Basic Handler
~~~~~~~~~~~~~

.. code-block:: kotlin

   on<PlayerJoinEvent> { event ->
       logger.info("{} joined", event.player.username)
   }

Priority
~~~~~~~~

.. code-block:: kotlin

   on<PlayerJoinEvent>(priority = EventPriority.HIGH) { event ->
       // runs before NORMAL handlers
   }

   on<PlayerJoinEvent>(priority = EventPriority.MONITOR) { event ->
       // always runs last, even if the event was cancelled
   }

Priority execution order:

.. mermaid::

   flowchart LR
       LOWEST --> LOW --> NORMAL --> HIGH --> HIGHEST --> MONITOR
       style MONITOR fill:#f0e6ff,stroke:#9b59b6

``MONITOR`` is the audit tier — it always fires regardless of cancellation state.

Cancellable Events
~~~~~~~~~~~~~~~~~~

.. code-block:: kotlin

   on<SomeCancellableEvent> { event ->
       if (shouldBlock) {
           event.isCancelled = true   // stops lower-priority handlers
       }
   }

Handlers at priorities below the cancelling handler are skipped. ``MONITOR``
handlers are exempt and always run.

Async Work Inside Handlers
~~~~~~~~~~~~~~~~~~~~~~~~~~

Handlers are ``suspend`` functions running in the plugin's ``CoroutineScope``:

.. code-block:: kotlin

   on<PlayerJoinEvent> { event ->
       launch {
           delay(3.seconds)
           // send welcome message, query a database, etc.
       }
   }

``onEnable`` Sugar
------------------

``onEnable { }`` is shorthand for ``on<ProxyInitializeEvent> { }`` at ``NORMAL``
priority. Use it for one-time startup work:

.. code-block:: kotlin

   onEnable {
       logger.info("Proxy is live with {} players", server.players.size)
   }

``onDisable``
-------------

``onDisable { }`` registers a teardown handler that runs when the proxy shuts down
gracefully (``stop`` command) or when plugins are unloaded. Multiple handlers are
allowed and run in registration order.

.. code-block:: kotlin

   onDisable {
       logger.info("Flushing cache…")
       cache.flush()   // suspend-friendly — you can call suspend functions here
   }

After all ``onDisable`` handlers complete:

1. All event handlers registered by this plugin are removed from the event bus.
2. All console commands registered by this plugin are removed.
3. The plugin's coroutine scope is cancelled (all running ``launch`` / ``every``
   jobs are stopped).

Plugin Commands
---------------

``command("name") { args -> }`` registers a console command that becomes available
immediately after the plugin enables. The full ``VectorPluginScope`` is the receiver,
so ``server``, ``logger``, and coroutine helpers are all in scope.

.. code-block:: kotlin

   command("hello") { args ->
       val who = args.firstOrNull() ?: "World"
       logger.info("Hello, {}!", who)
   }

   command("greet") { args ->
       val target = args.firstOrNull()
       if (target == null) {
           logger.warn("Usage: greet <player>")
           return@command
       }
       val player = server.getPlayer(target)
       if (player == null) {
           logger.warn("Player '{}' is not online", target)
       } else {
           logger.info("Greeting {} ({})", player.username, player.uuid)
       }
   }

The command name appears in tab completion and in ``help`` output under a *Plugin
commands* section. Commands are unregistered automatically when the plugin disables.

Scheduled Tasks — ``every``
----------------------------

``every(period) { }`` launches a repeating coroutine that fires once per ``period``,
starting after the first interval elapses. The returned ``Job`` is a child of the
plugin's scope and is cancelled automatically when the plugin disables.

.. code-block:: kotlin

   every(30.seconds) {
       logger.debug("Heartbeat: {} player(s) online", server.players.size)
   }

   every(5.minutes) {
       // flush metrics, expire stale sessions, etc.
   }

The block runs in the plugin's ``CoroutineScope`` (``Dispatchers.Default``), so
``launch``, ``delay``, and other coroutine primitives work as expected. If the block
throws, the exception is silently swallowed and the schedule continues — wrap in
``try/catch`` if you need to handle errors.

Lifecycle Example — Full Plugin
---------------------------------

The ``hello-world-plugin`` bundled in the repo demonstrates all lifecycle features:

.. code-block:: kotlin

   class HelloWorldPlugin : VectorPlugin({

       val sessions = ConcurrentHashMap<UUID, Instant>()
       val totalJoins = AtomicLong(0)

       onEnable {
           logger.info("Hello from Vector! Running proxy v{}", server.version)
       }

       onDisable {
           logger.info("HelloWorld shutting down ({} total joins this session).", totalJoins.get())
       }

       every(30.seconds) {
           logger.debug("Heartbeat: {} player(s) online", server.players.size)
       }

       command("hello") { args ->
           val who = args.firstOrNull() ?: "World"
           logger.info("Hello, {}!", who)
       }

       on<PlayerJoinEvent> { event ->
           sessions[event.player.uuid] = Instant.now()
           val count = totalJoins.incrementAndGet()
           logger.info("{} joined ({} online, {} total)", event.player.username,
               server.players.size, count)

           launch {
               delay(2.seconds)
               logger.info("Welcome, {}! You are player #{}.", event.player.username, count)
           }
       }

       on<PlayerLeaveEvent> { event ->
           val joinedAt = sessions.remove(event.player.uuid)
           if (joinedAt != null) {
               val d = Duration.between(joinedAt, Instant.now())
               logger.info("{} was online for {}m {}s",
                   event.player.username, d.toMinutes(), d.seconds % 60)
           }
       }
   })

Key points demonstrated:

- **Shared state**: ``sessions`` and ``totalJoins`` are captured in the lambda
  closure and live for the plugin's lifetime.
- **onDisable**: logs a summary before shutdown; runs before scope cancellation.
- **every**: the heartbeat job is cancelled automatically on disable.
- **command**: ``hello`` is available at the proxy console immediately after enable.
- **launch**: a fire-and-forget coroutine inside an event handler is safe — the
  ``SupervisorJob`` means a crash in the delayed block does not affect other handlers.

Dependency Ordering
-------------------

Hard dependencies guarantee the other plugin is fully enabled before yours:

.. mermaid::

   flowchart TD
       db["database-plugin\n(no deps)"]
       auth["auth-plugin\nhard-deps = [database-plugin]"]
       chat["chat-plugin\nhard-deps = [auth-plugin]"]
       ui["ui-plugin\nhard-deps = [auth-plugin]"]

       db -->|wave 1| auth
       auth -->|wave 2| chat
       auth -->|wave 2| ui

       style db fill:#dff0d8
       style auth fill:#d9edf7
       style chat fill:#fcf8e3
       style ui fill:#fcf8e3

Plugins in the same wave load in parallel; waves are sequential.
A cycle in the hard-dep graph is a startup error.

Class Loading
-------------

Each plugin gets an isolated ``URLClassLoader``. The delegation order is:

.. mermaid::

   flowchart TB
       Plugin["Plugin class\ne.g. com.example.MyPlugin"]

       subgraph PCL["PluginClassLoader (child-first)"]
           direction TB
           Own["plugin JAR"] -->|not found| Cross["other PluginClassLoaders\n(cross-plugin sharing)"]
       end

       subgraph Parent["Proxy ClassLoader (parent-first)"]
           API["dev.vector.api.*"]
           KT["kotlin.* / kotlinx.*"]
           SLF["org.slf4j.*"]
           Java["java.* / javax.*"]
       end

       Plugin --> PCL
       Cross -->|not found| Parent

       style API fill:#d9edf7
       style KT fill:#d9edf7
       style SLF fill:#d9edf7
       style Java fill:#d9edf7

``dev.vector.api.*`` is **always** loaded from the proxy classloader so that
``ProxyServer`` instances passed to plugins are the same ``Class<?>`` objects the
proxy uses — preventing ``ClassCastException`` across loader boundaries.

Two plugins can share classes with each other by importing directly; the
cross-loader fallback handles it automatically.
