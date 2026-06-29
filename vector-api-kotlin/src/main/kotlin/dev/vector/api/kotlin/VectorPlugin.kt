package dev.vector.api.kotlin

import dev.vector.api.event.ProxyShutdownEvent

open class VectorPlugin(private val init: VectorPluginScope.() -> Unit) {
    private var pluginScope: VectorPluginScope? = null

    fun enable(scope: VectorPluginScope) {
        pluginScope = scope
        scope.init()
    }

    suspend fun disable(event: ProxyShutdownEvent = ProxyShutdownEvent()) {
        pluginScope?.runDisable(event)
        pluginScope = null
    }
}
