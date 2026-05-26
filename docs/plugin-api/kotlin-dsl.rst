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
       // launch a fire-and-forget coroutine — crash here won't kill the proxy
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

Lifecycle Example — Session Tracking
--------------------------------------

The ``hello-world-plugin`` bundled in the repo demonstrates the full lifecycle:

.. code-block:: kotlin

   class HelloWorldPlugin : VectorPlugin({

       val sessions = ConcurrentHashMap<UUID, Instant>()
       val totalJoins = AtomicLong(0)

       onEnable {
           logger.info("Hello from Vector! Running proxy v{}", server.version)
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

       on<PlayerJoinEvent>(priority = EventPriority.MONITOR) { event ->
           logger.debug("[monitor] join pipeline complete for {}", event.player.username)
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
- **Coroutine launch**: ``launch { delay(...) }`` is safe — the ``SupervisorJob``
  means a crash in the delayed block does not affect other handlers.
- **MONITOR audit**: runs after all other ``PlayerJoinEvent`` handlers, even
  if one cancelled.

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
