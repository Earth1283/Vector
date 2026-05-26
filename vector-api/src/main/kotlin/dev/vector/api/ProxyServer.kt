package dev.vector.api

import dev.vector.api.event.EventBus
import dev.vector.api.storage.StorageBackend
import java.util.UUID

interface ProxyServer {
    val version: String
    val eventBus: EventBus
    val storage: StorageBackend
    val players: Collection<VectorPlayer>
    fun getPlayer(uuid: UUID): VectorPlayer?
    fun getPlayer(username: String): VectorPlayer?
    fun registerCommand(name: String, pluginId: String, handler: suspend (List<String>) -> Unit)
    fun unregisterCommands(pluginId: String)
}
