package dev.vector.proxy.plugin

import com.akuleshov7.ktoml.Toml
import dev.vector.api.kotlin.VectorPlugin
import dev.vector.api.kotlin.VectorPluginScope
import dev.vector.api.plugin.PluginContainer
import dev.vector.proxy.VectorServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

class PluginManager(private val server: VectorServer) {

    private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    private val _plugins = mutableListOf<PluginContainer>()
    val plugins: List<PluginContainer> get() = _plugins

    suspend fun loadPlugins(pluginsDir: Path) {
        if (!pluginsDir.exists()) return

        val jars = pluginsDir.listDirectoryEntries().filter { it.extension == "jar" }
        val nodes = jars.mapNotNull { jar ->
            try {
                loadManifest(jar)?.let { PluginNode(it, jar) }
            } catch (e: Exception) {
                logger.warn("Failed to read manifest from {}: {}", jar.fileName, e.message)
                null
            }
        }
        if (nodes.isEmpty()) return

        val waves = try {
            computeWaves(nodes)
        } catch (e: IllegalStateException) {
            logger.error("Plugin load aborted: {}", e.message)
            return
        }

        val parentLoader = javaClass.classLoader
        coroutineScope {
            for (wave in waves) {
                wave.map { node ->
                    async(Dispatchers.Default) { instantiatePlugin(node, parentLoader) }
                }.awaitAll().filterNotNull().forEach { _plugins.add(it) }
            }
        }

        for (container in _plugins) {
            enablePlugin(container)
        }
    }

    private fun loadManifest(jar: Path): dev.vector.api.plugin.PluginManifest? {
        JarFile(jar.toFile()).use { jf ->
            val entry = jf.getJarEntry("vector-plugin.toml") ?: run {
                logger.warn("{} has no vector-plugin.toml, skipping", jar.fileName)
                return null
            }
            val toml = jf.getInputStream(entry).bufferedReader().readText()
            return Toml.decodeFromString<RawManifest>(toml).toManifest()
        }
    }

    private fun instantiatePlugin(node: PluginNode, parent: ClassLoader): PluginContainer? {
        return try {
            val loader = PluginClassLoader(node.jarPath.toUri().toURL(), parent)
            val clazz = loader.loadClass(node.manifest.entrypoint)
            val instance = clazz.getDeclaredConstructor().newInstance()
            PluginContainer(node.manifest, instance, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        } catch (e: Exception) {
            logger.error("Failed to instantiate plugin {}: {}", node.manifest.id, e.message)
            null
        }
    }

    private fun enablePlugin(container: PluginContainer) {
        val (manifest, instance, scope) = container
        val pluginLogger = LoggerFactory.getLogger(manifest.id)
        when (instance) {
            is VectorPlugin -> {
                logger.info("Enabling plugin {} v{}", manifest.name, manifest.version)
                instance.enable(VectorPluginScope(server, pluginLogger, manifest.id, scope))
            }
            else -> logger.warn("Plugin {} does not extend VectorPlugin, skipping enable", manifest.id)
        }
    }
}
