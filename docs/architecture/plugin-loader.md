# Plugin Loader

The plugin loader lives in `dev.vector.proxy.plugin` and runs at proxy startup
after the Netty server socket is bound.

---

## Loading pipeline

```mermaid
flowchart TD
    Start([VectorServer.start\nafter Netty bind])
    Scan["Scan plugins/ dir\nfor *.jar files"]
    Manifest["Open each JAR\nread vector-plugin.toml\n→ RawManifest → PluginManifest"]
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
```

Instantiation within a wave is parallel (`async(Dispatchers.Default)`).
Waves are sequential — a plugin in wave N is guaranteed that every plugin it
declared in `hard-deps` has been instantiated and enabled.

---

## Dependency wave algorithm

```mermaid
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
```

`computeWaves()` is a greedy BFS: each iteration collects all nodes whose
hard-deps are satisfied, emits them as a wave, marks them loaded, repeats.
A non-empty remaining set with no satisfiable nodes means a cycle — startup
aborts with a clear error.

`soft-deps` are not enforced by the wave algorithm. They are reserved for
future use (e.g. ordering within a wave, optional service lookup).

---

## Class loader hierarchy

```mermaid
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
```

**Lookup order for any class name:**

1. Is it `dev.vector.api.*`, `kotlin.*`, `kotlinx.*`, `java.*`, `org.slf4j.*`?
   → delegate straight to parent (guarantees shared type identity).
2. Is it already loaded by *this* loader? → return cached.
3. Does *this* JAR contain it? → define and return.
4. Does any *other* `PluginClassLoader` contain it? → borrow and return.
5. Delegate to parent (proxy classpath).

Step 4 is the cross-plugin class sharing mechanism. Plugin A can depend on
classes shipped by Plugin B without bundling them — the same `Class<?>` object
is returned from both loaders so `instanceof` and casts work correctly.

---

## Plugin manifest reference

```toml
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
```

The manifest is read from `vector-plugin.toml` at the root of the JAR
(i.e. `src/main/resources/vector-plugin.toml` in a standard Gradle layout).

---

## PluginContainer

After a plugin is instantiated and enabled, `PluginManager` holds a
`PluginContainer` for it:

```kotlin
data class PluginContainer(
    val manifest: PluginManifest,
    val instance: Any,           // the VectorPlugin subclass instance
    val scope: CoroutineScope,   // SupervisorJob + Dispatchers.Default
)
```

The `scope` is the same one passed to `VectorPluginScope`. If the proxy ever
needs to unload a plugin, cancelling this scope cancels every coroutine the
plugin launched.
