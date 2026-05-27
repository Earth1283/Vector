package dev.vector.compat

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginManager
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.Optional

class VelocityPluginManagerShim : PluginManager {

    private val byId = LinkedHashMap<String, PluginContainer>()
    private val byInstance = IdentityHashMap<Any, PluginContainer>()

    fun registerPlugin(container: VelocityContainerShim) {
        byId[container.getDescription().getId()] = container
        container.getInstance().ifPresent { byInstance[it] = container }
    }

    override fun fromInstance(instance: Any): Optional<PluginContainer> =
        Optional.ofNullable(byInstance[instance])

    override fun getPlugin(id: String): Optional<PluginContainer> =
        Optional.ofNullable(byId[id])

    override fun getPlugins(): Collection<PluginContainer> = byId.values.toList()

    override fun isLoaded(id: String): Boolean = byId.containsKey(id)

    override fun addToClasspath(plugin: Any, path: Path) {
        val container = fromInstance(plugin).orElse(null) ?: return
        val inst = container.getInstance().orElse(null) ?: return
        val cl = inst.javaClass.classLoader
        if (cl is java.net.URLClassLoader) {
            try {
                val addUrl = java.net.URLClassLoader::class.java.getDeclaredMethod("addURL", java.net.URL::class.java)
                addUrl.isAccessible = true
                addUrl.invoke(cl, path.toUri().toURL())
            } catch (_: Exception) {}
        }
    }
}
