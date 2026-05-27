package dev.vector.compat

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginManager
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.Optional

class VelocityPluginManagerShim(private val vectorServer: dev.vector.api.ProxyServer) : PluginManager {

    private val byId = LinkedHashMap<String, PluginContainer>()
    private val byInstance = IdentityHashMap<Any, PluginContainer>()

    fun registerPlugin(container: VelocityContainerShim) {
        byId[container.getDescription().getId()] = container
        container.getInstance().ifPresent { byInstance[it] = container }
    }

    override fun fromInstance(instance: Any): Optional<PluginContainer> =
        Optional.ofNullable(byInstance[instance])

    override fun getPlugin(id: String): Optional<PluginContainer> {
        val shim = byId[id]
        if (shim != null) return Optional.of(shim)
        
        // Try to find a native plugin and wrap it
        // This is a bit complex because we need a VelocityDescription for it.
        // We'll leave it as a TODO for now if it's not strictly needed for this bug.
        return Optional.empty()
    }

    override fun getPlugins(): Collection<PluginContainer> = byId.values.toList()

    override fun isLoaded(id: String): Boolean = byId.containsKey(id)

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
}
