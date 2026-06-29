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
        } catch (e: Throwable) {
            val cause = if (e is java.lang.reflect.InvocationTargetException) e.cause ?: e else e
            logger.error("Failed to instantiate velocity plugin {} from {}: {}", manifest.id, jarPath.fileName, cause.message ?: cause.toString(), cause)
            return null
        }

        container.setInstance(instance)
        proxyShim.pluginManagerShim.registerPlugin(container)

        // auto-register @Subscribe methods from the plugin instance
        try {
            proxyShim.eventManagerShim.register(instance, instance)
        } catch (e: Throwable) {
            logger.warn("Failed to register @Subscribe handlers for {}: {}", manifest.id, e.message ?: e.toString())
        }

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
            put(CommandManager::class.java, VelocityCommandManagerShimDelegator(proxyShim.commandManagerShim, description.getId()))
            put(PluginContainer::class.java, container)
            put(PluginDescription::class.java, description)
            put(Logger::class.java, LoggerFactory.getLogger(description.getId()))
            put(ComponentLogger::class.java, ComponentLogger.logger(description.getId()))
            put(java.util.logging.Logger::class.java, java.util.logging.Logger.getLogger(description.getId()))
            put(Path::class.java, dataDir)
            put(ExecutorService::class.java, container.getExecutorService())
        }

        try {
            val injector = com.google.inject.Guice.createInjector(object : com.google.inject.AbstractModule() {
                override fun configure() {
                    for ((c, instance) in bindings) {
                        if (c == java.util.logging.Logger::class.java) continue
                        @Suppress("UNCHECKED_CAST")
                        bind(c as Class<Any>).toInstance(instance)
                    }
                    bind(Path::class.java).annotatedWith(DataDirectory::class.java).toInstance(dataDir)
                }
            })
            return injector.getInstance(clazz)
        } catch (guiceEx: Throwable) {
            logger.warn("Guice instantiation failed for legacy plugin {}, falling back to reflection: {}", 
                description.getId(), guiceEx.message ?: guiceEx.toString())
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
                val boundValue = bindings[param.type] 
                    ?: bindings.entries.firstOrNull { param.type.isAssignableFrom(it.key) }?.value
                boundValue ?: throw IllegalArgumentException("No binding for constructor parameter type ${param.type.name} in $mainClass")
            }
        }

        val instance = try {
            ctor.newInstance(*args.toTypedArray())
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }

        // Support field injection for legacy plugins (e.g. LuckPerms)
        var currentClass: Class<*>? = clazz
        while (currentClass != null && currentClass != Any::class.java) {
            for (field in currentClass.declaredFields) {
                if (field.isAnnotationPresent(Inject::class.java)) {
                    val annotation = field.getAnnotation(DataDirectory::class.java)
                    val value = if (annotation != null) {
                        dataDir
                    } else {
                        bindings[field.type] ?: bindings.entries.firstOrNull { field.type.isAssignableFrom(it.key) }?.value
                    }

                    if (value != null) {
                        try {
                            field.isAccessible = true
                            field.set(instance, value)
                        } catch (e: Exception) {
                            logger.warn("Failed to inject field {} in {}: {}", field.name, mainClass, e.message)
                        }
                    }
                }
            }
            currentClass = currentClass.superclass
        }

        return instance
    }
}

