Java Plugin API
===============

Java plugins compile against ``vector-api`` only — they do not need the Kotlin
DSL module. The Java API surface consists of plain Java interfaces with no
Kotlin-specific constructs.

.. note::

   **Status:** The ``VectorJavaPlugin`` base class and ``@Subscribe`` annotation
   processor are planned for Part 7. The interfaces and event types in
   ``vector-api`` are already usable from Java today.

Dependency
----------

Groovy DSL:

.. code-block:: groovy

   dependencies {
       compileOnly "dev.vector:vector-api:1.0.0-SNAPSHOT"
   }

Kotlin DSL:

.. code-block:: kotlin

   dependencies {
       compileOnly(project(":vector-api"))
   }

Plugin Manifest
---------------

.. code-block:: toml

   id         = "my-java-plugin"
   name       = "My Java Plugin"
   version    = "1.0.0"
   api-version = "1.0"
   entrypoint = "com.example.MyJavaPlugin"
   language   = "JAVA"

Setting ``language = "JAVA"`` tells the plugin loader to instantiate the class
via reflection rather than through the Kotlin ``VectorPlugin`` DSL path.

Plugin Class (Planned API)
---------------------------

.. code-block:: java

   public class MyJavaPlugin extends VectorJavaPlugin {

       @Override
       public void onEnable(ProxyServer server, Logger logger) {
           logger.info("Java plugin enabled! Proxy v{}", server.getVersion());

           server.getEventBus().register(
               PlayerJoinEvent.class,
               this,
               EventPriority.NORMAL,
               this::onPlayerJoin
           );
       }

       private void onPlayerJoin(PlayerJoinEvent event) {
           getLogger().info("{} joined", event.getPlayer().getUsername());
       }

       @Override
       public void onDisable() {
           getLogger().info("Disabled.");
       }
   }

Async Event Handlers (Java)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Java plugin handlers are invoked as ``CompletableFuture``-returning methods.
Vector wraps the future in the plugin's coroutine scope for structured
cancellation:

.. code-block:: java

   private CompletableFuture<Void> onPlayerJoin(PlayerJoinEvent event) {
       return CompletableFuture.runAsync(() -> {
           // Blocking work is OK here — it runs on the plugin dispatcher
           String welcome = database.fetchWelcome(event.getPlayer().getUuid());
           getLogger().info("Welcome message for {}: {}", event.getPlayer().getUsername(), welcome);
       }, getPluginExecutor());
   }

``getPluginExecutor()`` returns an ``Executor`` backed by the plugin's
``CoroutineScope`` dispatcher. Using any other executor bypasses structured
concurrency and is not recommended.

Using ``ProxyServer`` from Java
--------------------------------

All ``ProxyServer`` methods are ``@NotNull``-annotated and return Java-compatible
types:

.. code-block:: java

   ProxyServer server = getServer();

   // Player lookup
   Optional<VectorPlayer> player = Optional.ofNullable(server.getPlayer(uuid));

   // Online player list
   Collection<? extends VectorPlayer> online = server.getPlayers();

   // Event bus — direct registration without DSL
   server.getEventBus().register(
       PlayerLeaveEvent.class,
       pluginId,
       EventPriority.NORMAL,
       event -> logger.info("{} left", event.getPlayer().getUsername())
   );

Event Registration from Java
-----------------------------

The ``EventBus`` interface is Java-friendly. In practice, use the ``@Subscribe``
annotation (Part 7) which generates the correct adapter automatically:

.. code-block:: java

   @Subscribe(priority = EventPriority.HIGH)
   public void onJoin(PlayerJoinEvent event) {
       // ...
   }

Java Plugin Lifecycle
---------------------

.. mermaid::

   flowchart TD
       Load["PluginClassLoader\nloads entrypoint class"]
       Instantiate["getDeclaredConstructor().newInstance()\n(no-arg constructor required)"]
       Cast{instanceof\nVectorJavaPlugin?}
       Enable["onEnable(server, logger) called"]
       Running["Plugin running\n@Subscribe handlers active"]
       Disable["onDisable() called\n(on proxy shutdown or\nexplicit plugin unload)"]
       Skip["warn: unknown plugin type\nskipped"]

       Load --> Instantiate --> Cast
       Cast -->|yes| Enable --> Running --> Disable
       Cast -->|no| Skip

Unlike the Kotlin DSL path, the Java plugin's ``onEnable`` receives ``server`` and
``logger`` as parameters rather than through a scope receiver. This matches the
Velocity plugin API convention that Java developers are familiar with.
