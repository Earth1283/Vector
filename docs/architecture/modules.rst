Module Structure
================

Vector is split into five Gradle modules. The dependency graph is strictly
one-directional — nothing in a lower layer can import a type from a layer above
it. This is enforced by the Gradle project structure; there are no circular
dependencies.

Dependency Graph
----------------

.. mermaid::

   graph TD
       API["vector-api\nPublic plugin API\nProxyServer · VectorPlayer\nEventBus · PluginManifest\nAll plugin authors compile against this"]

       AK["vector-api-kotlin\nKotlin DSL sugar\nVectorPlugin { }\nVectorPluginScope\non&lt;T&gt; · onEnable { }"]

       Compat["vector-compat\nVelocity shim\nImplements velocity-api interfaces\nDelegates to vector-api types"]

       Proxy["vector-proxy\nCore implementation\nNetty pipeline · codec\nPlugin loader · EventBusImpl\nSession handlers · state machine"]

       HW["hello-world-plugin\n(example)\ncompileOnly only"]

       VelAPI["velocity-api\n(compileOnly — not bundled)"]

       API --> AK
       API --> Compat
       AK --> Proxy
       Compat --> Proxy
       API --> Proxy
       AK -.->|compileOnly| HW
       VelAPI -.->|compileOnly| Compat

       style API fill:#d9edf7,stroke:#31708f
       style AK fill:#dff0d8,stroke:#3c763d
       style Compat fill:#fcf8e3,stroke:#8a6d3b
       style Proxy fill:#f2dede,stroke:#a94442
       style HW fill:#e8e8e8,stroke:#999
       style VelAPI fill:#f5f5f5,stroke:#ccc,stroke-dasharray:5

The dashed arrows are ``compileOnly`` — those JARs are not bundled and must be
present on the classpath at runtime through other means.

``vector-api``
--------------

The public plugin API surface. Every plugin author — Kotlin or Java — compiles
against this module alone (plus ``vector-api-kotlin`` for Kotlin DSL sugar).

**Key interfaces and types:**

.. list-table::
   :header-rows: 1
   :widths: 25 30 45

   * - Type
     - Package
     - Description
   * - ``ProxyServer``
     - ``dev.vector.api``
     - Proxy singleton. Exposes ``players``, ``eventBus``, ``version``, ``getPlayer()``.
   * - ``VectorPlayer``
     - ``dev.vector.api``
     - Live player handle. ``uuid``, ``username``, ``disconnect(reason)``.
   * - ``BackendServer``
     - ``dev.vector.api``
     - Backend server descriptor. ``name``.
   * - ``EventBus``
     - ``dev.vector.api.event``
     - Register and fire typed events.
   * - ``VectorEvent``
     - ``dev.vector.api.event``
     - Base class for all events.
   * - ``CancellableEvent``
     - ``dev.vector.api.event``
     - Events that handlers can cancel.
   * - ``EventPriority``
     - ``dev.vector.api.event``
     - ``LOWEST → MONITOR`` execution order.
   * - ``ProxyInitializeEvent``
     - ``dev.vector.api.event``
     - Fired once after bind + plugin load.
   * - ``PlayerJoinEvent``
     - ``dev.vector.api.event``
     - Player fully connected to a backend.
   * - ``PlayerLeaveEvent``
     - ``dev.vector.api.event``
     - Player TCP connection closed.
   * - ``PluginManifest``
     - ``dev.vector.api.plugin``
     - Parsed plugin descriptor.
   * - ``PluginContainer``
     - ``dev.vector.api.plugin``
     - Live plugin reference: manifest, instance, scope.
   * - ``PluginLanguage``
     - ``dev.vector.api.plugin``
     - ``KOTLIN | JAVA``

**Exported dependencies** (available transitively to plugins):

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Library
     - Version
     - Why
   * - ``adventure-api``
     - 4.17.0
     - Text component and chat API
   * - ``slf4j-api``
     - 2.0.13
     - Logging facade
   * - ``kotlinx-coroutines-core``
     - 1.8.1
     - Coroutine scope, launch, delay

**Constraints:**

- Zero dependency on Netty, Velocity, ktoml, or any proxy-internal library.
- All interfaces are expressible in idiomatic Java — no Kotlin-only constructs
  at the API boundary (no inline functions, no extension properties on the
  interfaces themselves).

``vector-api-kotlin``
---------------------

The Kotlin DSL layer. Kotlin plugin authors add this as a ``compileOnly``
dependency to get the ``VectorPlugin { }`` builder and coroutine-native event
registration.

**Key types:**

.. list-table::
   :header-rows: 1
   :widths: 25 75

   * - Type
     - Description
   * - ``VectorPlugin``
     - Open class. Constructor takes ``VectorPluginScope.() -> Unit``. ``enable(scope)`` executes it.
   * - ``VectorPluginScope``
     - DSL receiver. Implements ``CoroutineScope``. Exposes ``server``, ``logger``, ``on<T>``, ``onEnable``.

**Why separate from** ``vector-api``?

Java plugins must not be forced to pull in Kotlin DSL symbols. The split is
opt-in: Kotlin plugin authors add both; Java plugin authors add only ``vector-api``.

This also keeps the core API surface expressible in plain Java, which matters
for the Velocity compat shim.

``vector-compat``
-----------------

The Velocity compatibility shim. Depends on ``velocity-api`` as ``compileOnly``
(not bundled in the proxy distribution — loaded by a classloader at runtime)
and implements Velocity's ``ProxyServer``, ``EventManager``, ``CommandManager``, and
other interfaces by delegating to ``vector-api`` equivalents.

**Compatibility guarantee:**

   If a plugin worked on Velocity 3.x, it works on Vector. This is a hard
   constraint, not a goal. A PR that breaks any Velocity plugin does not merge.

Enforcement mechanism:

.. mermaid::

   flowchart LR
       PR[Pull request] --> VecTest
       VecTest{VecTest suite\n138 endpoints} -->|all pass| Merge[Merge allowed]
       VecTest -->|any fail| Block[PR blocked]
       Night([Nightly]) --> AptMC[apt-mc\nModrinth top-20 plugins]
       AptMC -->|any fail| Issue[Auto-create tracking issue]

``vector-proxy``
----------------

The core implementation. Everything that requires Netty, ktoml, or proxy
internals lives here. Depends on all three modules above.

**Key packages:**

.. list-table::
   :header-rows: 1
   :widths: 45 55

   * - Package
     - Contents
   * - ``dev.vector.proxy``
     - ``VectorServer`` (implements ``ProxyServer``), ``VectorProxy`` (main entry)
   * - ``dev.vector.proxy.network``
     - ``MinecraftConnection``, Netty handlers, ``SessionHandler``, ``BackendConnection``
   * - ``dev.vector.proxy.network.session``
     - Per-state session handler implementations
   * - ``dev.vector.proxy.protocol``
     - ``ProtocolVersion``, ``StateRegistry``, ``DirectionRegistry``, packet codecs
   * - ``dev.vector.proxy.protocol.packet.*``
     - Packet classes (handshake, status, login)
   * - ``dev.vector.proxy.event``
     - ``EventBusImpl``
   * - ``dev.vector.proxy.plugin``
     - ``PluginManager``, ``PluginClassLoader``, ``PluginNode``, ``RawManifest``
   * - ``dev.vector.proxy.model``
     - ``VectorPlayer`` (impl), ``GameProfile``, ``BackendServerInfo``
   * - ``dev.vector.proxy.config``
     - ``VectorConfig`` (ktoml)
   * - ``dev.vector.proxy.crypto``
     - ``CryptoUtils``, ``MojangAuth``

**Future extraction point:** A ``vector-protocol`` sub-module is a clean
extraction point if the packet/codec layer outgrows ``vector-proxy``. It is
not created upfront to avoid premature modularisation.

``hello-world-plugin``
----------------------

A real Gradle subproject that compiles against ``vector-api-kotlin`` using
``compileOnly``. Its sole purpose is to prove the plugin API compiles and
demonstrate DSL usage.

It is **not** a test fixture — it is the reference example for new plugin
authors and is kept up to date with every API change.

See the :doc:`../plugin-api/kotlin-dsl` for a walkthrough.
