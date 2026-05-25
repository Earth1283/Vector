package dev.vector.example

import dev.vector.api.event.PlayerJoinEvent
import dev.vector.api.event.PlayerLeaveEvent
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.api.kotlin.VectorPlugin

class HelloWorldPlugin : VectorPlugin({
    onEnable {
        logger.info("Hello from Vector! Proxy is ready.")
    }

    on<PlayerJoinEvent> { event ->
        logger.info("{} joined the proxy", event.player.username)
    }

    on<PlayerLeaveEvent> { event ->
        logger.info("{} left the proxy", event.player.username)
    }
})
