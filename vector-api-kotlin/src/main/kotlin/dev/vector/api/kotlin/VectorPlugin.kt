package dev.vector.api.kotlin

abstract class VectorPlugin(block: VectorPlugin.() -> Unit) {
    init {
        block()
    }
}
