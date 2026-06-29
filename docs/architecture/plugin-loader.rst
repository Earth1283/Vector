Plugin Loader
=============

The plugin loader lives in ``dev.vector.proxy.plugin`` and runs at proxy startup
after the Netty server socket is bound.

Loading Pipeline
----------------

.. mermaid::

   flowchart TD
       Start([VectorServer.start\nafter Netty bind])
       Scan["Scan plugins/ dir\nfor *.jar files"]
       Manifest["Open each JAR once\ndetect manifest type + read contents\n→ PluginNode (manifest + path)"]
       Wave["computeWaves(nodes)\nRespects hard-deps order"]

       subgraph Wave1["Wave 1 — no dependencies"]
           W1A["plugin-a.jar\nasync instantiate"]
           W1B["plugin-b.jar\nasync instantiate"]
       end

       subgraph Wave2["Wave 2 — depends on wave 1"]
           W2A["plugin-c.jar\nasync instantiate"]
       end

       Enable["Sequential enable()\ncall each VectorPlugin.enable(scope)"]
       Fire["fire(ProxyInitializeEvent)\nonEnable handlers run"]
       Done([Ready])

       Start --> Scan --> Manifest --> Wave
       Wave --> Wave1
       Wave1 -->|awaitAll| Wave2
       Wave2 -->|awaitAll| Enable
       Enable --> Fire --> Done

Instantiation within a wave is parallel (``async(Dispatchers.Default)``).
Waves are sequential — a plugin in wave N is guaranteed that every plugin it
declared in ``hard-deps`` has been instantiated and enabled.

Dependency Wave Algorithm
--------------------------

.. mermaid::

   flowchart LR
       A["plugin-a\nno deps"]
       B["plugin-b\nno deps"]
       C["plugin-c\nhard-deps=[a]"]
       D["plugin-d\nhard-deps=[a, b]"]
       E["plugin-e\nhard-deps=[c, d]"]

       subgraph W1[Wave 1]
           A
           B
       end
       subgraph W2[Wave 2]
           C
           D
       end
       subgraph W3[Wave 3]
           E
       end

       W1 --> W2 --> W3

``computeWaves()`` is a greedy BFS: each iteration collects all nodes whose
hard-deps are satisfied, emits them as a wave, marks them loaded, repeats.
A non-empty remaining set with no satisfiable nodes means a cycle — startup
aborts with a clear error.

``soft-deps`` are not enforced by the wave algorithm. They are reserved for
future use (e.g. ordering within a wave, optional service lookup).

Class Loader Hierarchy
-----------------------

.. mermaid::

   graph TD
       Boot["Bootstrap ClassLoader\nJDK rt.jar"]
       Platform["Platform ClassLoader\nJDK modules"]
       App["App ClassLoader\nproxy classpath:\nvector-api, vector-api-kotlin,\nNetty, coroutines, …"]

       subgraph Plugins["One PluginClassLoader per JAR"]
           P1["PluginClassLoader\nplugin-a.jar\nchild-first"]
           P2["PluginClassLoader\nplugin-b.jar\nchild-first"]
           P3["PluginClassLoader\nplugin-c.jar\nchild-first"]
       end

       Boot --> Platform --> App --> P1 & P2 & P3
       P1 <-->|"cross-loader\nfallback"| P2
       P2 <-->|"cross-loader\nfallback"| P3

**Lookup order for any class name:**

1. Is it ``dev.vector.api.*``, ``kotlin.*``, ``kotlinx.*``, ``java.*``, ``org.slf4j.*``?
   → delegate straight to parent (guarantees shared type identity).
2. Is it already loaded by *this* loader? → return cached.
3. Does *this* JAR contain it? → define and return.
4. Does any *other* ``PluginClassLoader`` contain it? → borrow and return.
5. Delegate to parent (proxy classpath).

Step 4 is the cross-plugin class sharing mechanism. Plugin A can depend on
classes shipped by Plugin B without bundling them — the same ``Class<?>`` object
is returned from both loaders so ``instanceof`` and casts work correctly.

Plugin Manifest Reference
--------------------------

.. code-block:: toml

   # Required
   id          = "my-plugin"          # unique across all loaded plugins
   version     = "1.0.0"
   entrypoint  = "com.example.MyPlugin"   # fully-qualified class name

   # Optional with defaults
   name        = "My Plugin"          # display name, falls back to id
   api-version = "1.0"
   language    = "KOTLIN"             # KOTLIN | JAVA

   # Dependency declarations
   hard-deps   = ["database-plugin"]  # must be present and loaded first
   soft-deps   = ["stats-plugin"]     # load before this plugin if present

The ``language`` field determines the loading mechanism:

- ``KOTLIN`` (default): Loads as a native Vector plugin. The entrypoint must
  extend ``VectorPlugin``.
- ``JAVA``: Loads through the Velocity compatibility layer. The entrypoint
  is treated as a Velocity plugin and undergoes Guice/reflection injection.

The manifest is read from ``vector-plugin.toml`` at the root of the JAR
(i.e. ``src/main/resources/vector-plugin.toml`` in a standard Gradle layout).

If your plugin has both mainfests, ``vector-plugin.toml`` will be read and executed.

PluginContainer
---------------

After a plugin is instantiated and enabled, ``PluginManager`` holds a
``PluginContainer`` for it:

.. code-block:: kotlin

   data class PluginContainer(
       val manifest: PluginManifest,
       val instance: Any,           // the VectorPlugin subclass instance
       val scope: CoroutineScope,   // SupervisorJob + Dispatchers.Default
       val classLoader: ClassLoader,// the PluginClassLoader for this JAR
   )

The ``scope`` is the same one passed to ``VectorPluginScope``. If the proxy ever
needs to unload a plugin, cancelling this scope cancels every coroutine the
plugin launched. The ``classLoader`` is retained so subsystems like Flyway can
locate classpath resources (e.g. migration SQL files) bundled inside the plugin
JAR even after the JAR is fully loaded.

Multi-Core Utilization
-----------------------

.. note::

   **For sysadmins:** Vector fully utilizes multi-core hardware during plugin
   loading. Unlike older single-threaded proxy loaders, plugins within the same
   dependency wave are instantiated in **parallel** across all available CPU cores
   using Kotlin coroutines on ``Dispatchers.Default``.

   ``Dispatchers.Default`` sizes itself to ``availableProcessors`` threads by
   default. On an 8-core host, up to 8 plugins can be instantiated simultaneously
   — classloading, JAR scanning, and constructor execution all run concurrently.
   On a 32-core host, 32 plugins can load in parallel.

   **Practical impact:** A network with 20 independent plugins (no
   inter-dependencies) loads all 20 in approximately the time it takes to load
   one, limited only by JAR I/O and classloading overhead. A sequential loader
   would take 20× longer.

   The dependency ordering guarantee is still fully enforced — plugins in later
   waves only start after every plugin in earlier waves has been fully
   instantiated. Parallelism applies *within* a wave, not across waves.

   .. code-block:: text

      # Example: 10 independent plugins on a 4-core machine
      Wave 1 (10 plugins, 4 CPU cores):

      Core 1: plugin-a  ████████ done
      Core 2: plugin-b  ██████ done
      Core 3: plugin-c  ████████████ done
      Core 4: plugin-d  ███████ done
      Core 1: plugin-e  ████████ done    # picks up next job
      Core 2: plugin-f  █████ done
      Core 3: plugin-g  ████████ done
      Core 4: plugin-h  ██████ done
      Core 1: plugin-i  ███████ done
      Core 2: plugin-j  ████ done
                        |---------|
                        Wall time ≈ 3× longest single plugin load
                        (vs. 10× on a sequential loader)

   To take full advantage of this, avoid declaring ``hard-deps`` on plugins that
   do not actually need to be initialized first — unnecessary hard dependencies
   force sequential waves and reduce parallelism.
