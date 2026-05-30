package dev.vector.compat

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.plugin.PluginManager
import com.velocitypowered.api.plugin.meta.PluginDependency
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.Optional

class VelocityPluginManagerShim(
    private val vectorServer: dev.vector.api.ProxyServer,
    // Lazily supplies the proxy's loaded native plugins. A lambda is used so this
    // module needs no compile dependency on vector-proxy (which would be a cycle),
    // and so the set stays current as plugins continue to load.
    private val nativePlugins: () -> Collection<dev.vector.api.plugin.PluginContainer> = { emptyList() },
) : PluginManager {

    private val byId = LinkedHashMap<String, PluginContainer>()
    private val byInstance = IdentityHashMap<Any, PluginContainer>()
    private val nativeWrapped = LinkedHashMap<String, PluginContainer>()

    fun registerPlugin(container: VelocityContainerShim) {
        byId[container.getDescription().getId()] = container
        container.getInstance().ifPresent { byInstance[it] = container }
    }

    override fun fromInstance(instance: Any): Optional<PluginContainer> =
        Optional.ofNullable(byInstance[instance])

    override fun getPlugin(id: String): Optional<PluginContainer> {
        byId[id]?.let { return Optional.of(it) }
        return Optional.ofNullable(wrapNative(id))
    }

    override fun getPlugins(): Collection<PluginContainer> {
        // Velocity plugins expect to see every loaded plugin, native ones included.
        val all = LinkedHashMap<String, PluginContainer>(byId)
        for (native in nativePlugins()) {
            val nid = native.manifest.id
            if (nid !in all) wrapNative(nid)?.let { all[nid] = it }
        }
        return all.values.toList()
    }

    override fun isLoaded(id: String): Boolean =
        byId.containsKey(id) || nativePlugins().any { it.manifest.id == id }

    override fun addToClasspath(plugin: Any, path: Path) {
        val container = fromInstance(plugin).orElse(null) ?: return
        val inst = container.getInstance().orElse(null) ?: return
        val cl = inst.javaClass.classLoader
        if (cl is dev.vector.api.plugin.PluginClassLoader) {
            cl.addJar(path.toUri().toURL())
        } else if (cl is java.net.URLClassLoader) {
            // fallback for non-vector loaders if any
            try {
                val addUrl = java.net.URLClassLoader::class.java.getDeclaredMethod("addURL", java.net.URL::class.java)
                addUrl.isAccessible = true
                addUrl.invoke(cl, path.toUri().toURL())
            } catch (_: Exception) {}
        }
    }

    // Wraps a native Vector plugin as a Velocity PluginContainer so legacy plugins can
    // discover it via the PluginManager. Wrappers are cached so identity stays stable.
    private fun wrapNative(id: String): PluginContainer? {
        nativeWrapped[id]?.let { return it }
        val native = nativePlugins().find { it.manifest.id == id } ?: return null
        val container = VelocityContainerShim(NativePluginDescription(native.manifest))
        container.setInstance(native.instance)
        byInstance[native.instance] = container
        nativeWrapped[id] = container
        return container
    }

    // Adapts a native Vector PluginManifest to a Velocity PluginDescription.
    // PluginManifest carries no description/url/author metadata, so those stay empty.
    private class NativePluginDescription(
        private val manifest: dev.vector.api.plugin.PluginManifest,
    ) : PluginDescription {
        override fun getId(): String = manifest.id
        override fun getVersion(): Optional<String> = Optional.of(manifest.version)
        override fun getDependencies(): Collection<PluginDependency> =
            manifest.hardDeps.map { PluginDependency(it, null, false) } +
                manifest.softDeps.map { PluginDependency(it, null, true) }
        override fun getSource(): Optional<Path> = Optional.empty()
    }
}
