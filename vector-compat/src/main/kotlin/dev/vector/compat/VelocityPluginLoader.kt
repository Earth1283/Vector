package dev.vector.compat

import com.google.inject.Inject
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import dev.vector.api.plugin.PluginLanguage
import dev.vector.api.plugin.PluginManifest
import dev.vector.api.plugin.PluginClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.jar.JarFile
import dev.vector.api.plugin.PluginContainer as VectorPluginContainer

class VelocityPluginLoader(
    private val proxyShim: VelocityProxyServerShim,
    private val pluginsDir: Path,
) {
    private val logger = LoggerFactory.getLogger(VelocityPluginLoader::class.java)

    fun loadAndEnable(jarPath: Path): VectorPluginContainer? {
        val manifest = readManifest(jarPath) ?: return null
        val description = VelocityDescriptionShim(manifest, jarPath)

        val container = VelocityContainerShim(description)

        val classLoader = PluginClassLoader(
            jarPath.toUri().toURL(),
            javaClass.classLoader,
        )

        val instance = try {
            instantiate(jarPath, manifest.main, description, container, classLoader)
        } catch (e: Exception) {
            logger.error("Failed to instantiate velocity plugin {} from {}: {}", manifest.id, jarPath.fileName, e.message)
            return null
        }

        container.setInstance(instance)
        proxyShim.pluginManagerShim.registerPlugin(container)

        // auto-register @Subscribe methods from the plugin instance
        proxyShim.eventManagerShim.register(instance, instance)

        logger.info("Enabled legacy Velocity plugin {} v{} by {}",
            description.getName().orElse(manifest.id),
            description.getVersion().orElse("?"),
            description.getAuthors().joinToString(", ").ifEmpty { "?" })

        val nativeManifest = PluginManifest(
            id = manifest.id,
            name = manifest.name ?: manifest.id,
            version = manifest.version ?: "1.0.0",
            apiVersion = "1.0.0",
            entrypoint = manifest.main,
            language = PluginLanguage.JAVA,
            hardDeps = manifest.dependencies.filter { !it.optional }.map { it.id },
            softDeps = manifest.dependencies.filter { it.optional }.map { it.id }
        )

        return VectorPluginContainer(
            manifest = nativeManifest,
            instance = instance,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            classLoader = classLoader
        )
    }

    private fun readManifest(jarPath: Path): VelocityManifest? {
        return try {
            JarFile(jarPath.toFile()).use { jar ->
                val entry = jar.getJarEntry("velocity-plugin.json") ?: return null
                val json = jar.getInputStream(entry).bufferedReader().readText()
                VelocityManifest.parse(json).takeIf { it.id.isNotBlank() && it.main.isNotBlank() }
            }
        } catch (e: Exception) {
            logger.warn("Failed to read velocity-plugin.json from {}: {}", jarPath.fileName, e.message)
            null
        }
    }

    private fun instantiate(
        jarPath: Path,
        mainClass: String,
        description: VelocityDescriptionShim,
        container: VelocityContainerShim,
        classLoader: ClassLoader,
    ): Any {
        val clazz = classLoader.loadClass(mainClass)
        val dataDir = pluginsDir.resolve(description.getId())
        Files.createDirectories(dataDir)

        val bindings: Map<Class<*>, Any> = buildMap {
            put(ProxyServer::class.java, proxyShim)
            put(EventManager::class.java, proxyShim.eventManagerShim)
            put(CommandManager::class.java, proxyShim.commandManagerShim)
            put(PluginContainer::class.java, container)
            put(PluginDescription::class.java, description)
            put(Logger::class.java, LoggerFactory.getLogger(description.getId()))
            put(ComponentLogger::class.java, ComponentLogger.logger(description.getId()))
            put(Path::class.java, dataDir)
            put(ExecutorService::class.java, container.getExecutorService())
        }

        // Find the @Inject constructor, or fall back to a single constructor
        val ctor = clazz.constructors
            .firstOrNull { c -> c.isAnnotationPresent(Inject::class.java) }
            ?: clazz.constructors.singleOrNull()
            ?: throw IllegalStateException("No suitable constructor found in $mainClass")

        val args = ctor.parameters.map { param ->
            val annotation = param.getAnnotation(DataDirectory::class.java)
            if (annotation != null) {
                dataDir
            } else {
                bindings[param.type]
                    ?: throw IllegalArgumentException("No binding for constructor parameter type ${param.type.name} in $mainClass")
            }
        }

        return ctor.newInstance(*args.toTypedArray())
    }
}
