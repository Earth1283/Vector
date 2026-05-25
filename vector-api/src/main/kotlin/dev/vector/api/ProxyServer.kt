package dev.vector.api

import dev.vector.api.event.EventBus
import java.util.UUID

interface ProxyServer {
    val version: String
    val eventBus: EventBus
    val players: Collection<VectorPlayer>
    fun getPlayer(uuid: UUID): VectorPlayer?
    fun getPlayer(username: String): VectorPlayer?
}
