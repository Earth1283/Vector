package dev.vector.proxy

import dev.vector.proxy.config.VectorConfig

fun main() {
    val config = VectorConfig.load()
    VectorServer(config).start()
}
