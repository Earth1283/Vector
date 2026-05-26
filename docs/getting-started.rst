Getting Started
===============

Vector is a Kotlin + Netty Minecraft proxy. This page walks you from a fresh
clone to a running proxy and your first plugin.

Prerequisites
-------------

.. list-table::
   :header-rows: 1
   :widths: 15 15 70

   * - Requirement
     - Version
     - Notes
   * - Java
     - 21
     - Microsoft OpenJDK 21 recommended. The Gradle Kotlin DSL compiler cannot
       parse Java 25 version strings — pin to 21.
   * - Gradle
     - 8.11+
     - Wrapper is bundled; no manual install needed.
   * - Git
     - any
     -

Setting up Java in Codespaces
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In the GitHub Codespace the correct JDK lives at
``/usr/local/sdkman/candidates/java/21.0.10-ms``. The repo's ``gradle.properties``
already pins ``org.gradle.java.home`` to this path so Gradle daemon launches work
out of the box.

If you are working locally, set ``JAVA_HOME`` to your Java 21 installation:

.. code-block:: bash

   export JAVA_HOME=/path/to/jdk-21
   export PATH=$JAVA_HOME/bin:$PATH
   java -version   # should print openjdk 21...

Project Layout
--------------

.. mermaid::

   graph TD
       Root["vector/\n(root project)"]
       API["vector-api\nPublic plugin API surface\nNo Velocity, no Netty"]
       AK["vector-api-kotlin\nKotlin DSL layer\nVectorPlugin { }"]
       Compat["vector-compat\nVelocity compatibility shim\n(compileOnly: velocity-api)"]
       Proxy["vector-proxy\nCore implementation\nNetty · codec · plugins · state"]
       HW["hello-world-plugin\nExample plugin\n(compileOnly: vector-api-kotlin)"]

       Root --> API
       Root --> AK
       Root --> Compat
       Root --> Proxy
       Root --> HW
       API --> AK
       API --> Compat
       AK --> Proxy
       Compat --> Proxy
       AK -.->|compileOnly| HW

       style API fill:#d9edf7,stroke:#31708f
       style AK fill:#dff0d8,stroke:#3c763d
       style Proxy fill:#f2dede,stroke:#a94442

The dependency arrow goes **upward** — lower modules know nothing about
higher ones. ``vector-api`` has zero knowledge of Netty or Velocity types.

Building
--------

.. code-block:: bash

   ./gradlew build

This compiles all five modules, runs tests, and produces JARs under each
module's ``build/libs/``.

To build without tests:

.. code-block:: bash

   ./gradlew build -x test

To build a single module:

.. code-block:: bash

   ./gradlew :vector-proxy:build

Building a fat JAR
------------------

.. code-block:: bash

   ./gradlew shadowJar
   java -jar vector-proxy/build/libs/vector.jar

Produces a single self-contained JAR (~30 MB) with all dependencies bundled.
No Gradle or JDK installation required on the target machine beyond a JRE 21+.

Running the Proxy
-----------------

Development (Gradle)
~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:run

From the assembled distribution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:installDist
   ./vector-proxy/build/install/vector-proxy/bin/vector-proxy

This is the production path — no Gradle daemon, no recompilation on startup.

On first start the proxy writes a default ``vector.toml`` to the working
directory and then binds:

.. code-block:: text

   INFO  Generating RSA key pair...
   INFO  RSA key pair ready
   INFO  Proxy bound on 0.0.0.0:25565
   INFO  Vector is ready (1.234s) — Type 'help' for commands.

What Happens at Startup
-----------------------

.. mermaid::

   flowchart TD
       Main([main]) --> LoadCfg[Load / generate\nvector.toml]
       LoadCfg --> KeyPair[Generate RSA key pair\nfor online-mode auth]
       KeyPair --> ParseServers[Parse servers map\nfrom config]
       ParseServers --> NettyBind[Netty bind\nboss + worker groups\nepoll / kqueue / NIO]
       NettyBind --> Plugins[PluginManager.loadPlugins\nscan plugins/ dir]
       Plugins --> Waves[Compute load waves\nrespecting hard-deps]
       Waves --> Parallel[Parallel instantiate\nwithin each wave]
       Parallel --> Enable[Sequential enable\nruns VectorPlugin blocks]
       Enable --> InitEvent[fire ProxyInitializeEvent\nonEnable handlers run]
       InitEvent --> Accept([Accept connections])

Connecting Your First Client
-----------------------------

1. Start the proxy with ``./gradlew :vector-proxy:run``.
2. Start a backend Minecraft server in **offline mode** on port ``25577``
   (the default in ``vector.toml``).
3. Open the Minecraft client and add ``localhost`` as a server.
4. Connect — you should land in the backend world.

.. mermaid::

   sequenceDiagram
       participant C as Minecraft Client
       participant P as Vector Proxy
       participant B as Backend Server

       C->>P: TCP connect :25565
       C->>P: Handshake (nextState=login)
       C->>P: LoginStart (username, uuid)
       P->>P: Mojang session auth
       P->>B: TCP connect :25577
       P->>B: Handshake + LoginStart
       B-->>P: LoginSuccess
       P-->>C: LoginSuccess (+ SetCompression)
       Note over P,C: packet codec removed, raw forwarding starts
       C<-->>P<-->>B: Play packets forwarded transparently

Build Order
-----------

.. list-table::
   :header-rows: 1
   :widths: 10 70 10

   * - Part
     - Milestone
     - Status
   * - 1
     - Netty pipeline boots, accepts TCP
     - ✓
   * - 2
     - Handshake + Status — appears in server list
     - ✓
   * - 3
     - Login flow — player connects, gets kicked cleanly
     - ✓
   * - 4
     - Encryption + Mojang auth (online mode)
     - ✓
   * - 5
     - Basic backend forwarding — player reaches a server
     - ✓
   * - 5.1
     - MinecraftConnection + SessionHandler refactor
     - ✓
   * - 5.2
     - VectorServer + VectorPlayer + ServerRegistry
     - ✓
   * - 5.3
     - Packet compression + player-info forwarding
     - ✓
   * - 6
     - Plugin loader boots, loads hello-world plugin
     - ✓
   * - 7.1
     - Full player state machine (compiler-enforced transitions)
     - ✓
   * - 7.2
     - jline3 console — theme DSL, frecency autocomplete, ghost text
     - ✓
   * - 7.3
     - Plugin lifecycle: onDisable, command DSL, scheduled tasks
     - ✓
   * - 7.4
     - Storage — SQLite, Flyway migrations, pluggable backends
     - ✓
   * - 7.5
     - MOTD + full configuration system
     - ✓
   * - 7.6
     - Cluster + Limbo
     -
   * - 7.7
     - Velocity compat wiring + VecTest suite
     -

Writing Your First Plugin
--------------------------

Create a new Gradle project (or subproject) with:

.. code-block:: kotlin

   // build.gradle.kts
   dependencies {
       compileOnly(project(":vector-api-kotlin"))
   }

Add a manifest at ``src/main/resources/vector-plugin.toml``:

.. code-block:: toml

   id         = "my-first-plugin"
   name       = "My First Plugin"
   version    = "1.0.0"
   entrypoint = "com.example.MyFirstPlugin"
   language   = "KOTLIN"

Write the plugin class:

.. code-block:: kotlin

   class MyFirstPlugin : VectorPlugin({
       onEnable {
           logger.info("Hello, Vector! Running v{}", server.version)
       }

       on<PlayerJoinEvent> { event ->
           logger.info("{} joined ({} online)",
               event.player.username, server.players.size)
       }
   })

Build the plugin JAR, drop it into ``plugins/``, and start the proxy. You should
see your log lines when the proxy starts and when a player connects.

See :doc:`../plugin-api/kotlin-dsl` for the complete plugin API reference.
