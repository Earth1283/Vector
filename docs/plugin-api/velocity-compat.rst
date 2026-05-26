Velocity Compatibility
======================

Existing Velocity plugins run unmodified on Vector. Drop a Velocity plugin JAR
into the ``plugins/`` directory — if it contains a ``velocity-plugin.json`` manifest
(Velocity's native format), Vector loads it through the compatibility layer
instead of the native plugin loader.

.. note::

   **Status:** The compat classloader is in ``vector-compat`` and will be wired
   into the plugin loader in Part 7. The shim architecture documented here is
   finalised — only the wiring remains.

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
           SM["VelocitySchedulerShim\nimplements Scheduler"]
           PM["VelocityPlayerShim\nimplements RegisteredServer"]
       end

       subgraph Vector["vector internals"]
           VS["VectorServer\n(ProxyServer)"]
           EB["EventBusImpl"]
       end

       VP --> PS & EM & CM & SM
       PS --> VS
       EM --> EB
       CM --> VS
       SM --> VS

Each shim implements the corresponding Velocity API interface and delegates
every call to the equivalent Vector internal. The Velocity plugin never sees a
Vector type directly — it only ever holds references to the shim implementations.

Shim Implementations
---------------------

``VelocityProxyServerShim``
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: kotlin

   class VelocityProxyServerShim(private val server: VectorServer) : ProxyServer {

       override fun getPlayer(username: String): Optional<Player> =
           Optional.ofNullable(server.getPlayer(username))
               ?.map { VelocityPlayerShim(it) }

       override fun getAllPlayers(): Collection<Player> =
           server.players.map { VelocityPlayerShim(it) }

       override fun getPluginManager(): PluginManager = VelocityPluginManagerShim(server)

       override fun getEventManager(): EventManager = VelocityEventManagerShim(server.eventBus)

       override fun getCommandManager(): CommandManager = VelocityCommandManagerShim(server)

       override fun getScheduler(): Scheduler = VelocitySchedulerShim(server.proxyScope)

       override fun getVersion(): ProxyVersion =
           ProxyVersion("Vector", "Vector", server.version)
   }

``VelocityEventManagerShim``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The most complex shim. Velocity's event system uses ``@Subscribe`` annotations
and the ``EventTask`` return type for async events. The shim:

1. Scans for ``@Subscribe``-annotated methods on the listener object.
2. Wraps each handler in a ``suspend (T) -> Unit`` lambda.
3. Registers the lambda with ``EventBusImpl`` using the ``@Subscribe`` priority.

.. mermaid::

   flowchart TD
       Register["eventManager.register(plugin, listenerObject)"]
       Scan["Reflect on listenerObject\nfind @Subscribe methods"]
       ForEach["For each method:\nresolve event type, priority"]
       Wrap["Wrap method.invoke() in\nsuspend lambda\nhandle EventTask async returns"]
       RegEB["eventBus.register(eventClass, pluginId, priority, lambda)"]

       Register --> Scan --> ForEach --> Wrap --> RegEB

Compat Classloader
------------------

Velocity plugins may depend on ``velocity-api`` types at runtime. The compat
classloader exposes these types by loading ``velocity-api`` from the proxy
classpath (where it lives as a ``compileOnly`` dependency of ``vector-compat``).

.. mermaid::

   graph TD
       CompatCL["CompatClassLoader\nextends URLClassLoader"]
       VelAPI["velocity-api.jar\n(on proxy classpath via\nvector-compat compileOnly)"]
       PluginJAR["legacy-plugin.jar"]
       Parent["Proxy classloader\n(vector-api, Netty, etc.)"]

       CompatCL -->|"loadClass velocity.*\n→ parent delegation"| VelAPI
       CompatCL -->|"loadClass plugin.*\n→ child-first"| PluginJAR
       CompatCL -->|"not found anywhere else"| Parent

``velocity-api`` types loaded by the compat classloader are the **same** ``Class<?>``
**objects** as those loaded by the proxy classloader, because both use parent
delegation for ``com.velocitypowered.*`` classes. This is critical — if two
classloaders each loaded ``ProxyServer.class``, ``instanceof`` checks would fail
across the boundary.

Load Log Output
---------------

.. code-block:: text

   INFO  [plugin] EssentialsX  v2.20.1  legacy velocity  compat mode
   INFO  [plugin] MyPlugin     v1.0.0   vector native    api 1.0
   INFO  [plugin] WeirdPlugin  v0.1.0   rejected         no manifest

Compatibility Guarantee
------------------------

   If your plugin worked on Velocity 3.x, it works on Vector. This is a hard
   constraint, not a feature goal.

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
