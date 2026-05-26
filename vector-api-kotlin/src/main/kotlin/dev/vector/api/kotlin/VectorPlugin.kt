package dev.vector.api.kotlin

open class VectorPlugin(private val init: VectorPluginScope.() -> Unit) {
    private var pluginScope: VectorPluginScope? = null

    fun enable(scope: VectorPluginScope) {
        pluginScope = scope
        scope.init()
    }

    suspend fun disable() {
        pluginScope?.runDisable()
        pluginScope = null
    }
}
