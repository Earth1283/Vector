package dev.vector.proxy.plugin

import com.akuleshov7.ktoml.Toml
import dev.vector.api.VectorJavaPlugin
import dev.vector.api.event.ProxyShutdownEvent
import dev.vector.api.kotlin.VectorPlugin
import dev.vector.api.kotlin.VectorPluginScope
import dev.vector.api.plugin.PluginClassLoader
import dev.vector.api.plugin.PluginContainer
import dev.vector.compat.VelocityCommandManagerShim
import dev.vector.compat.VelocityEventManagerShim
import dev.vector.compat.VelocityPluginLoader
import dev.vector.compat.VelocityPluginManagerShim
import dev.vector.compat.VelocityProxyServerShim
import dev.vector.compat.VelocitySchedulerShim
import dev.vector.proxy.VectorServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.coroutines.coroutineContext
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

class PluginManager(private val server: VectorServer) {

    private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    private val _plugins = mutableListOf<PluginContainer>()
    val plugins: List<PluginContainer> get() = _plugins

    private var velocityProxyShim: VelocityProxyServerShim? = null

    suspend fun loadPlugins(pluginsDir: Path) {
        if (!pluginsDir.exists()) return

        val jars = pluginsDir.listDirectoryEntries().filter { it.extension == "jar" }
        if (jars.isEmpty()) return

        val nodes = jars.mapNotNull { jar ->
            try {
                // Detect type and load manifest in a single JarFile open.
                readPluginNode(jar)
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

        // Initialize Velocity compatibility layer only for legacy velocity-plugin.json plugins
        if (nodes.any { it.isVelocityCompat }) {
            val eventMgr = VelocityEventManagerShim(server)
            val cmdMgr = VelocityCommandManagerShim(server)
            val sched = VelocitySchedulerShim()
            val pluginMgr = VelocityPluginManagerShim(server) { _plugins.toList() }
            velocityProxyShim = VelocityProxyServerShim(server, eventMgr, cmdMgr, sched, pluginMgr)
        }

        val parentLoader = javaClass.classLoader
        coroutineScope {
            for (wave in waves) {
                wave.map { node ->
                    async(Dispatchers.Default) {
                        if (node.isVelocityCompat && velocityProxyShim != null) {
                            val loader = VelocityPluginLoader(velocityProxyShim!!, pluginsDir)
                            loader.loadAndEnable(node.jarPath)
                        } else {
                            instantiatePlugin(node, parentLoader)
                        }
                    }
                }.awaitAll().filterNotNull().forEach { _plugins.add(it) }
            }
        }

        for (container in _plugins) {
            if (container.instance is VectorPlugin || container.instance is VectorJavaPlugin) {
                enablePlugin(container)
            }
        }
    }

    /** Opens the JAR exactly once, detects the manifest type, and returns a [PluginNode] or null. */
    private fun readPluginNode(jar: Path): PluginNode? {
        return JarFile(jar.toFile()).use { jf ->
            when {
                jf.getJarEntry("vector-plugin.toml") != null -> {
                    val entry = jf.getJarEntry("vector-plugin.toml")!!
                    val toml = jf.getInputStream(entry).bufferedReader().readText()
                    val manifest = Toml.decodeFromString<RawManifest>(toml).toManifest()
                    PluginNode(manifest, jar, isVelocityCompat = false)
                }
                jf.getJarEntry("velocity-plugin.json") != null -> {
                    val entry = jf.getJarEntry("velocity-plugin.json")!!
                    val json = jf.getInputStream(entry).bufferedReader().readText()
                    val raw = dev.vector.compat.VelocityManifest.parse(json)
                    val manifest = dev.vector.api.plugin.PluginManifest(
                        id = raw.id,
                        name = raw.name ?: raw.id,
                        version = raw.version ?: "1.0.0",
                        apiVersion = "1.0.0",
                        entrypoint = raw.main,
                        language = dev.vector.api.plugin.PluginLanguage.JAVA,
                        hardDeps = raw.dependencies.filter { !it.optional }.map { it.id },
                        softDeps = raw.dependencies.filter { it.optional }.map { it.id },
                    )
                    PluginNode(manifest, jar, isVelocityCompat = true)
                }
                else -> {
                    logger.warn("{} has no plugin manifest, skipping", jar.fileName)
                    null
                }
            }
        }
    }

    suspend fun disableAll(event: ProxyShutdownEvent = ProxyShutdownEvent()) {
        for (container in _plugins.asReversed()) {
            disablePlugin(container, event)
        }
        _plugins.clear()
        velocityProxyShim?.shutdown()
    }

    private fun instantiatePlugin(node: PluginNode, parent: ClassLoader): PluginContainer? {
        return try {
            val loader = PluginClassLoader(node.jarPath.toUri().toURL(), parent)
            val clazz = loader.loadClass(node.manifest.entrypoint)
            val instance = clazz.getDeclaredConstructor().newInstance()
            PluginContainer(node.manifest, instance, CoroutineScope(SupervisorJob() + Dispatchers.Default), loader)
        } catch (e: Throwable) {
            logger.error("Failed to instantiate plugin {}: {}", node.manifest.id, e.message ?: e.toString())
            null
        }
    }

    private fun enablePlugin(container: PluginContainer) {
        val (manifest, instance, scope, classLoader) = container
        val pluginLogger = LoggerFactory.getLogger(manifest.id)
        when (instance) {
            is VectorPlugin -> {
                logger.info("Enabling plugin {} v{}", manifest.name, manifest.version)
                instance.enable(VectorPluginScope(server, pluginLogger, manifest.id, scope, classLoader))
            }
            is VectorJavaPlugin -> {
                logger.info("Enabling Java plugin {} v{}", manifest.name, manifest.version)
                instance.initPlugin(server, pluginLogger, manifest.id)
                try {
                    instance.onEnable()
                } catch (e: Exception) {
                    logger.error("Error in onEnable for {}: {}", manifest.id, e.message)
                }
            }
            else -> logger.warn("Plugin {} is not a recognized plugin type, skipping enable", manifest.id)
        }
    }

    private suspend fun disablePlugin(container: PluginContainer, event: ProxyShutdownEvent = ProxyShutdownEvent()) {
        val (manifest, instance, scope) = container
        logger.info("Disabling plugin {} v{}", manifest.name, manifest.version)
        when (instance) {
            is VectorPlugin -> {
                try {
                    instance.disable(event)
                } catch (e: Exception) {
                    logger.error("Error in onDisable for {}: {}", manifest.id, e.message)
                }
            }
            is VectorJavaPlugin -> {
                try {
                    instance.onDisable()
                } catch (e: Exception) {
                    logger.error("Error in onDisable for {}: {}", manifest.id, e.message)
                }
            }
        }
        server.eventBus.unregisterAll(manifest.id)
        server.unregisterCommands(manifest.id)
        scope.coroutineContext[Job]?.cancel()
    }
}
