Vector
======

**A Minecraft proxy. Fast. Open. Free. Always.**

Vector is a clean-room Minecraft proxy built on Kotlin and Netty. It is not a
fork of Velocity, BungeeCord, or anything else. Every architectural decision
was made deliberately, and most of them are documented in
``docs/architecture/design-decisions.rst`` if you want to argue with them.

.. list-table::
   :widths: 25 75

   * - **Language**
     - Kotlin (JVM 21)
   * - **Async I/O**
     - Netty (epoll/kqueue/NIO) + Kotlin coroutines
   * - **License**
     - AGPL-3.0 — intentionally not MIT
   * - **Plugin compat**
     - Velocity 3.x drop-in, native Kotlin DSL, plain Java

----

Why this exists
---------------

Velocity is good software. The problem is that the infrastructure around it —
closed commercial builds, the lack of an AGPL option, the coupling to Guice —
makes it unsuitable as a foundation for community-run infrastructure that needs
to stay permanently open.

Vector solves that by being a clean implementation of the same proxy concept
under a license that cannot be privatised. If you run Vector you are
contractually obligated to publish your changes. That is the point.

Why Kotlin
----------

Minecraft proxies are fundamentally concurrent. Every connection is a state
machine that has to survive I/O, blocking auth calls, and arbitrary plugin code
without stalling every other connection.

The traditional answer is thread pools and callbacks. The result is code that
is hard to read, hard to debug, and subtly wrong at scale. Kotlin coroutines
solve this at the language level. ``suspend`` is not syntactic sugar — it is the
threading model. ``proxyScope.launch { }`` is not a convenience — it is
structured concurrency. Sessions, auth, events, and plugin work all run in
the right place automatically.

Kotlin also gives us value-class state machines, sealed hierarchies for packet
types, and extension functions that keep protocol utilities out of hot-path
class bodies. None of this is available in Java without ceremony.

Why AGPL, not MIT
-----------------

MIT lets you take this code, make it better, and run it as a commercial service
without giving anything back. AGPL does not. If you distribute a modified
Vector or run it as a network service, you must publish your modifications.

This is a proxy for Minecraft communities. The people who benefit from it most
are the ones who have least leverage to negotiate with closed-source vendors.
AGPL is the right license for that use case. If AGPL is a dealbreaker for your
use case, open a conversation — but "I want to sell a closed binary of this"
is not a compelling argument.

Why not a Velocity fork
-----------------------

Forks carry forward the original design constraints. Velocity's internals are
structured around Guice injection, CompletableFuture event handling, and a
threading model that predates structured concurrency. Refactoring those out
would be harder than writing clean code, and the result would still carry the
old constraints in the parts that weren't touched.

Starting fresh meant we could make deliberate choices: the session-handler
state machine over enum+dispatch, native coroutines over thread pools,
``write`` + ``FlushConsolidationHandler`` over ``writeAndFlush`` per packet,
``ByteBuffer`` views in compression codecs over heap array copies. These
decisions compound. A fork cannot compound them cleanly.

The three laws
--------------

These are not design goals. They are axioms. Everything else has trade-offs.
These do not.

**1. Free forever.**
AGPL-3.0. No paid builds. No premium tiers. No "enterprise edition". No
features gated behind a subscription. Not now, not ever.

**2. Open always.**
Architecture decisions are made in public. The roadmap is in this repository.
There is no private fork that gets features first.

**3. We do not break userspace.**
If your Velocity 3.x plugin ran without depending on Velocity internals, it
runs on Vector. If a pull request breaks this, the pull request does not merge.
No exceptions, no grace periods, no "please upgrade your plugin." This is
enforced mechanically: the VecTest suite runs on every PR and a Modrinth
compatibility harness runs nightly against the top 20 Velocity plugins by
follower count. A failure blocks the release.

----

Quick start
-----------

.. code-block:: bash

   source ./setup.sh          # sets JAVA_HOME to the bundled JDK 21
   ./gradlew :vector-proxy:run

The proxy binds on ``*:25565``. A backend Minecraft server in offline mode
(default port 25577) is required before players can fully connect.

On first run, ``vector.toml`` is written to the working directory. Edit it to
point at your backend servers, then restart.

----

Prerequisites
-------------

+------------+---------+----------------------------------------------------------+
| Tool       | Version | Notes                                                    |
+============+=========+==========================================================+
| Java       | **21**  | Microsoft OpenJDK 21 is bundled in the devcontainer.     |
|            |         | Java 25 breaks the Kotlin DSL compiler; pin to 21.       |
+------------+---------+----------------------------------------------------------+
| Gradle     | 8.11+   | Wrapper is bundled; ``./gradlew`` downloads it for you.  |
+------------+---------+----------------------------------------------------------+
| Git        | any     |                                                          |
+------------+---------+----------------------------------------------------------+

**Codespaces / devcontainer**: ``source ./setup.sh`` sets ``JAVA_HOME`` to
``/home/codespace/java/21.0.10-ms``. The Gradle daemon picks it up
automatically via ``gradle.properties``.

**Local**: export ``JAVA_HOME`` before running Gradle:

.. code-block:: bash

   export JAVA_HOME=/path/to/jdk-21
   export PATH=$JAVA_HOME/bin:$PATH
   java -version   # should print openjdk 21.x.x

----

Build guide
-----------

All commands run from the repository root.

Build everything
~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew build

Compiles all modules, runs tests, and produces JARs under each module's
``build/libs/``.

Build a fat JAR (production)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew shadowJar
   java -jar vector-proxy/build/libs/vector.jar

A single self-contained JAR (~30 MB) with all dependencies bundled. The target
machine needs a JRE 21+ and nothing else.

Run the proxy (development)
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:run

Build the assembled distribution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   ./gradlew :vector-proxy:installDist
   ./vector-proxy/build/install/vector-proxy/bin/vector-proxy

No Gradle daemon, no recompilation on startup. This is the preferred path for
running on a server.

Run tests
~~~~~~~~~

.. code-block:: bash

   ./gradlew test

Build the documentation
~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   pip install -r requirements-docs.txt
   make -C docs html

Output lands at ``docs/_build/html/index.html``.

----

Modules
-------

+------------------------+---------------------------------------------------+
| ``vector-api``         | Java-friendly plugin API. No Velocity or Netty    |
|                        | dependency. This is the only module a Java plugin |
|                        | needs to compile against.                         |
+------------------------+---------------------------------------------------+
| ``vector-api-kotlin``  | Kotlin DSL (``VectorPlugin { on<Event> { } }``).  |
|                        | Opt-in; Java plugins don't need it.               |
+------------------------+---------------------------------------------------+
| ``vector-compat``      | Velocity 3.x compatibility shim. Implements the   |
|                        | full Velocity API surface against vector-api.     |
+------------------------+---------------------------------------------------+
| ``vector-proxy``       | Core: Netty pipelines, protocol codec, session    |
|                        | state machine, plugin loader, console.            |
+------------------------+---------------------------------------------------+
| ``hello-world-plugin`` | Example plugin. Not included in the proxy JAR —   |
|                        | build it separately and drop it into ``plugins/``.|
+------------------------+---------------------------------------------------+

The dependency graph is strict and one-directional. ``vector-proxy`` depends on
``vector-api-kotlin`` which depends on ``vector-api``. Lower modules have zero
knowledge of higher ones. ``vector-api`` has no Velocity, Netty, or coroutine
dependency — it is plain Kotlin interfaces and data classes. A plugin author
can compile a release against ``vector-api`` 1.0 and it will run on any Vector
version that ships that API.

----

Writing a plugin
----------------

Native Kotlin plugin
~~~~~~~~~~~~~~~~~~~~~

Add ``vector-api-kotlin`` to your ``build.gradle.kts``:

.. code-block:: kotlin

   dependencies {
       compileOnly(project(":vector-api-kotlin"))
   }

Create ``src/main/resources/vector-plugin.toml``:

.. code-block:: toml

   id         = "my-plugin"
   name       = "My Plugin"
   version    = "1.0.0"
   entrypoint = "com.example.MyPlugin"
   language   = "KOTLIN"

Write the plugin class:

.. code-block:: kotlin

   class MyPlugin : VectorPlugin({
       onEnable {
           logger.info("Hello from {}!", server.version)
       }
       on<PlayerJoinEvent> { event ->
           logger.info("{} joined", event.player.username)
       }
   })

Build the JAR and drop it into ``plugins/``. Dependencies between plugins are
declared in ``vector-plugin.toml`` with ``hard-deps`` and ``soft-deps``. The
loader resolves them into waves and instantiates each wave in parallel.

Velocity plugin (no changes required)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Drop an existing Velocity 3.x plugin JAR into ``plugins/``. If it contains a
``velocity-plugin.json`` manifest and does not reach into Velocity or Vector
internals via reflection, it will work. No recompilation, no code changes.

The compat layer implements the full ``com.velocitypowered.api.*`` surface
including ``@Subscribe``, ``EventManager``, ``CommandManager``, the scheduler,
and player/server abstractions. Events from Vector's native bus are bridged to
Velocity equivalents automatically.

See ``docs/plugin-api/`` for the full API reference and compatibility notes.

----

Architecture in one paragraph
-------------------------------

The Netty pipeline handles framing, encryption (AES/CFB8), compression (zlib),
and packet decode/encode on the event-loop thread — nothing blocks. A
``FlushConsolidationHandler`` at the head of every pipeline batches TCP flushes
so that all packets from a single game tick are written in one or two syscalls
instead of one per packet. Once login completes, the packet codec is removed and
raw ``ByteBuf``s are forwarded directly between channels. Everything above the
network layer — auth, events, plugin code, database work — runs in Kotlin
coroutines on ``Dispatchers.Default`` or ``Dispatchers.IO``. The two worlds
cross exactly once per request, at a deliberate boundary. See
``docs/architecture/`` for the full treatment.

----

License
-------

`AGPL-3.0 <LICENSE>`_. Community contributions stay community property.
