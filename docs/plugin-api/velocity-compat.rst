Velocity Compatibility
======================

Existing Velocity plugins run unmodified on Vector through a shim layer. Drop a
Velocity plugin JAR into the ``plugins/`` directory — if it contains a
``velocity-plugin.json`` manifest (Velocity's native format), Vector loads it
through the compatibility layer instead of the native plugin loader.

.. note::

   **Status: complete as of Part 7.7.** The full shim layer is implemented in
   ``vector-compat`` and wired into ``PluginManager``. Velocity plugins with a
   ``velocity-plugin.json`` manifest are loaded automatically alongside native
   plugins.

Hard Limits
-----------

.. danger::

   **Reflection into Velocity internals will not work. There are no plans to
   change this.**

   The compat layer exposes the Velocity API surface (interfaces, annotations,
   value types). It does **not** ship Velocity's internal implementation classes
   — ``com.velocitypowered.proxy.*`` and similar internal packages are not on the
   classpath at all. Any plugin that uses reflection to reach into Velocity
   implementation classes at runtime will throw ``ClassNotFoundException`` or
   ``NoSuchMethodException`` and will not be supported.

   This is not a bug. Velocity internals are not part of Velocity's public API
   contract, and Vector is not Velocity. Plugins that rely on them were already
   technically unsupported on Velocity itself.

.. danger::

   **Reflection into Vector internals is also unsupported.**

   ``dev.vector.proxy.*`` is not public API. Classes, methods, and fields in that
   package may be renamed, moved, or removed at any time without notice. Plugins
   that reach into Vector internals via reflection will break without warning and
   will receive no compatibility consideration in future releases.

   If you need functionality that is only available via Vector internals, open a
   feature request so it can be added to ``dev.vector.api``.

How Velocity Plugins are Detected
----------------------------------

.. mermaid::

   flowchart TD
       Scan["Scan plugins/ for *.jar"]
       CheckToml{vector-plugin.toml\npresent?}
       CheckVelocity{velocity-plugin.json\npresent?}
       NativeLoad["Load as native\nVector plugin"]
       CompatLoad["Load through\nVelocity compat layer"]
       Warn["Warn: no manifest\nSkip JAR"]

       Scan --> CheckToml
       CheckToml -->|yes| NativeLoad
       CheckToml -->|no| CheckVelocity
       CheckVelocity -->|yes| CompatLoad
       CheckVelocity -->|no| Warn

A JAR with both manifests is treated as a native Vector plugin. This allows
plugin authors to publish a single JAR that works on both Velocity and Vector
during a migration period.

Compat Layer Architecture
--------------------------

.. mermaid::

   graph LR
       VP["Velocity plugin\nuses velocity-api types"]

       subgraph Shims["vector-compat shims"]
           PS["VelocityProxyServerShim\nimplements ProxyServer"]
           EM["VelocityEventManagerShim\nimplements EventManager"]
           CM["VelocityCommandManagerShim\nimplements CommandManager"]
           SM["VelocitySchedulerShim\nimplements Scheduler\nown ScheduledExecutorService"]
           PP["VelocityPlayerShim\nimplements Player"]
           RS["VelocityRegisteredServerShim\nimplements RegisteredServer"]
           PL["VelocityPluginLoader\nconstructor injection"]
       end

       subgraph Vector["vector-api"]
           VS["ProxyServer\n(interface)"]
           EB["EventBus\n(interface)"]
       end

       VP --> PS & EM & CM & SM
       PS --> VS
       EM --> EB
       CM --> VS
       PL --> PS
       PS --> PP
       PS --> RS

Each shim implements the corresponding Velocity API interface and delegates
every call to the equivalent Vector internal. The Velocity plugin never sees a
Vector type directly — it only ever holds references to the shim implementations.

Shim Implementations
---------------------

``VelocityProxyServerShim``
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Constructed once per compat-load session and shared across all legacy plugins
loaded in that session. Takes the pre-built shim collaborators as constructor
parameters:

.. code-block:: kotlin

   class VelocityProxyServerShim(
       private val vectorServer: dev.vector.api.ProxyServer,
       val eventManagerShim: VelocityEventManagerShim,
       val commandManagerShim: VelocityCommandManagerShim,
       val schedulerShim: VelocitySchedulerShim,
       val pluginManagerShim: VelocityPluginManagerShim,
   ) : ProxyServer

Player and server lookups delegate to ``vectorServer`` and wrap results in
their respective shims (``VelocityPlayerShim``, ``VelocityRegisteredServerShim``).
``getVersion()`` returns ``ProxyVersion("Vector", "Vector Team", vectorServer.version)``.

``VelocityEventManagerShim``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Handles both ``@Subscribe``-annotated listener objects and functional
``EventHandler<E>`` registrations. It also bridges Vector events to Velocity
events in its ``init`` block:

.. code-block:: kotlin

   init {
       vectorServer.eventBus.register(PlayerJoinEvent::class, "vector-compat", ...) { event ->
           fireAndForget(PostLoginEvent(VelocityPlayerShim(event.player)))
       }
       vectorServer.eventBus.register(PlayerLeaveEvent::class, "vector-compat", ...) { event ->
           fireAndForget(DisconnectEvent(VelocityPlayerShim(event.player), ...))
       }
   }

``register(plugin, listener)`` scans for ``@Subscribe`` methods:

1. Finds all methods with a single parameter annotated with ``@Subscribe``.
2. Reads ``annotation.order`` (a Kotlin property access — **not** ``order()``).
3. Stores ``SubscriberEntry(plugin, listener, method, order)`` keyed by event type.

``fire(event)`` dispatches subscribers sorted by ``PostOrder.ordinal`` descending
(``LAST`` fires first), then dispatches functional ``EventHandler`` entries sorted
by priority descending. Returns ``CompletableFuture.completedFuture(event)``.

.. mermaid::

   flowchart TD
       Register["eventManager.register(plugin, listenerObject)"]
       Scan["Reflect on listenerObject\nfind @Subscribe methods (paramCount == 1)"]
       ForEach["For each method:\nread annotation.order, resolve event type"]
       Store["Store SubscriberEntry\nin ConcurrentHashMap keyed by event class"]

       Register --> Scan --> ForEach --> Store

``VelocityPluginLoader``
~~~~~~~~~~~~~~~~~~~~~~~~

Loads a single legacy JAR and performs manual constructor injection — Velocity
normally uses Guice for this; the compat layer does it explicitly instead.

.. mermaid::

   flowchart TD
       Load["loadAndEnable(jarPath)"]
       Manifest["Read velocity-plugin.json\nfrom JAR entry\n→ VelocityManifest"]
       Desc["Wrap in VelocityDescriptionShim\n(implements PluginDescription)"]
       CL["Create URLClassLoader\n(jarPath + proxy classloader as parent)"]
       Bindings["Build bindings map\nProxyServer, EventManager, CommandManager\nPluginContainer, PluginDescription\nLogger, ComponentLogger\nPath (@DataDirectory), ExecutorService"]
       Ctor["Find @Inject constructor\nor single constructor"]
       Resolve["Resolve each parameter\nfrom bindings map\n(@DataDirectory → data dir Path)"]
       Instantiate["ctor.newInstance(*args)"]
       Register["container.setInstance(instance)\npluginManager.registerPlugin(container)\neventManager.register(instance, instance)"]

       Load --> Manifest --> Desc --> CL --> Bindings --> Ctor --> Resolve --> Instantiate --> Register

Compat Classloader
------------------

Velocity plugins depend on ``velocity-api`` types at runtime. ``vector-compat``
declares ``velocity-api`` as an ``api`` dependency so it is bundled into the
shadow JAR and available on the proxy classpath. The per-plugin ``URLClassLoader``
uses the proxy classloader as its parent, so both loaders resolve
``com.velocitypowered.*`` to the same ``Class<?>`` objects via parent delegation.

.. mermaid::

   graph TD
       CompatCL["URLClassLoader\n(per legacy plugin JAR)"]
       VelAPI["velocity-api\n(bundled in proxy shadow JAR\nvia vector-compat api dep)"]
       PluginJAR["legacy-plugin.jar"]
       Parent["Proxy classloader\n(vector-api, Netty, velocity-api, etc.)"]

       CompatCL -->|"loadClass com.velocitypowered.*\n→ parent delegation"| VelAPI
       CompatCL -->|"loadClass plugin.*\n→ child-first from JAR"| PluginJAR
       CompatCL -->|"fallback"| Parent

``velocity-api`` types are resolved via parent delegation — both the proxy and the
plugin classloader see the same ``Class<?>`` objects. This is critical: if they
were loaded separately, ``instanceof ProxyServer`` checks across the boundary would
return ``false``.

Load Log Output
---------------

.. code-block:: text

   INFO  [plugin] EssentialsX  v2.20.1  legacy velocity  compat mode
   INFO  [plugin] MyPlugin     v1.0.0   vector native    api 1.0
   INFO  [plugin] WeirdPlugin  v0.1.0   rejected         no manifest

Compatibility Guarantee
------------------------

   If your plugin used only the Velocity public API (``com.velocitypowered.api.*``)
   and did not reflect into Velocity or Vector internals, it works on Vector.
   This is a hard constraint, not a feature goal.

Enforcement:

.. mermaid::

   flowchart LR
       PR["Pull request"] --> VecTest["VecTest suite\n138 API endpoint tests\nruns on every PR"]
       VecTest -->|all green| Merge["Merge allowed"]
       VecTest -->|any failure| Block["PR blocked —\nnot negotiable"]

       Night(["Nightly"]) --> AptMC["apt-mc\nDownload top-20 Velocity plugins\nfrom Modrinth by follower count\nRun against live Vector harness"]
       AptMC -->|all pass| OK["No issues"]
       AptMC -->|any failure| Issue["Auto-create GitHub\ntracking issue"]
       Issue -->|required plugin| ReleaseBlock["Blocks next release"]
       Issue -->|optional plugin| Warn["Warning only"]

There is no grace period. If a PR makes a Velocity plugin throw at startup or
misbehave at runtime, the PR does not merge until the regression is fixed or an
explicit compatibility exception is documented and approved.
