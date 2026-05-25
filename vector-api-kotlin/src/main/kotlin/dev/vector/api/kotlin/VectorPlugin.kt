package dev.vector.api.kotlin

open class VectorPlugin(private val init: VectorPluginScope.() -> Unit) {
    fun enable(scope: VectorPluginScope) {
        scope.init()
    }
}
