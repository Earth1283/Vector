package dev.vector.api

import dev.vector.api.event.EventBus
import dev.vector.api.storage.StorageBackend
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

interface ProxyServer {
    val version: String
    val eventBus: EventBus
    val storage: StorageBackend
    val players: Collection<VectorPlayer>
    val servers: Collection<BackendServer>
    val coroutineScope: CoroutineScope
    fun getPlayer(uuid: UUID): VectorPlayer?
    fun getPlayer(username: String): VectorPlayer?
    fun registerCommand(
        name: String,
        pluginId: String,
        handler: suspend (List<String>) -> Unit,
        completer: (List<String>) -> List<String> = { emptyList() }
    )
    fun unregisterCommand(name: String)
    fun unregisterCommands(pluginId: String)
    fun registerServer(name: String, address: java.net.InetSocketAddress): BackendServer
    fun unregisterServer(name: String)
}
