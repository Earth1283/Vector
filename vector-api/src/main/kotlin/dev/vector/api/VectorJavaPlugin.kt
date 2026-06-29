package dev.vector.api

import org.slf4j.Logger

abstract class VectorJavaPlugin {

    private var server_: ProxyServer? = null
    private var logger_: Logger? = null
    private var pluginId_: String = ""

    val server: ProxyServer get() = server_ ?: error("Plugin not yet initialized")
    val logger: Logger get() = logger_ ?: error("Plugin not yet initialized")
    val pluginId: String get() = pluginId_

    fun initPlugin(server: ProxyServer, logger: Logger, pluginId: String) {
        server_ = server
        logger_ = logger
        pluginId_ = pluginId
    }

    abstract fun onEnable()

    open fun onDisable() {}
}
