package dev.vector.proxy.plugin

import java.net.URL
import java.net.URLClassLoader

class PluginClassLoader(jarUrl: URL, parent: ClassLoader) : URLClassLoader(arrayOf(jarUrl), parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (
            name.startsWith("dev.vector.api.") ||
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
            return try {
                val clazz = findClass(name)
                if (resolve) resolveClass(clazz)
                clazz
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}
