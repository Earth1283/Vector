package dev.vector.api.plugin

import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.CopyOnWriteArrayList

class PluginClassLoader(jarUrl: URL, parent: ClassLoader) : URLClassLoader(arrayOf(jarUrl), parent) {

    companion object {
        private val allLoaders = CopyOnWriteArrayList<PluginClassLoader>()

        fun register(loader: PluginClassLoader) {
            allLoaders.add(loader)
        }

        fun unregister(loader: PluginClassLoader) {
            allLoaders.remove(loader)
        }
    }

    init {
        register(this)
    }

    fun findClassDirect(name: String): Class<*> {
        return findLoadedClass(name) ?: findClass(name)
    }

    public override fun addURL(url: URL) {
        super.addURL(url)
    }

    fun addJar(url: URL) {
        addURL(url)
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (
            name.startsWith("dev.vector.api.") ||
            name.startsWith("com.velocitypowered.api.") ||
            name.startsWith("net.kyori.adventure.") ||
            name.startsWith("net.kyori.examination.") ||
            name.startsWith("net.kyori.option.") ||
            name.startsWith("com.google.gson.") ||
            name.startsWith("com.google.inject.") ||
            name.startsWith("com.google.common.") ||
            name.startsWith("kotlin.") ||
            name.startsWith("kotlinx.") ||
            name.startsWith("java.") ||
            name.startsWith("javax.") ||
            name.startsWith("sun.") ||
            name.startsWith("org.slf4j.")
        ) {
            return super.loadClass(name, resolve)
        }
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            
            val selfClass = runCatching {
                val clazz = findClass(name)
                if (resolve) resolveClass(clazz)
                clazz
            }.getOrNull()

            if (selfClass != null) return selfClass

            for (loader in allLoaders) {
                if (loader === this) continue
                try {
                    val clazz = loader.findClassDirect(name)
                    if (resolve) resolveClass(clazz)
                    return clazz
                } catch (_: ClassNotFoundException) {
                }
            }

            return super.loadClass(name, resolve)
        }
    }
}
