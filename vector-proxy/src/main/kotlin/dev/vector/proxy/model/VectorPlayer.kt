package dev.vector.proxy.model

import dev.vector.proxy.VectorServer
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.play.PlayDisconnectPacket
import dev.vector.proxy.network.BackendConnection
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

class VectorPlayer(
    val profile: GameProfile,
    val connection: MinecraftConnection,
    val server: VectorServer,
) : dev.vector.api.VectorPlayer {

    override val uuid: UUID get() = profile.uuid
    override val username: String get() = profile.username
    val protocolVersion: ProtocolVersion get() = connection.protocolVersion
    val playerState: PlayerState get() = connection.playerState

    var currentServerInfo: BackendServerInfo? = null
    override val currentServer: dev.vector.api.BackendServer? get() = currentServerInfo
    var currentBackendConn: MinecraftConnection? = null

    var pendingConnection: CompletableDeferred<Boolean>? = null

    override fun disconnect(reason: String) {
        if (connection.playerState is PlayerState.InServer) {
            val escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"")
            connection.closeWith(PlayDisconnectPacket("""{"text":"$escaped"}"""))
        } else {
            connection.close()
        }
    }

    override fun sendMessage(jsonText: String) {
        if (connection.playerState is PlayerState.InServer) {
            connection.write(dev.vector.proxy.protocol.packet.play.SystemChatPacket(message = jsonText))
        }
    }

    override suspend fun connect(server: dev.vector.api.BackendServer): Boolean {
        val info = server as? BackendServerInfo ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pendingConnection = deferred
        
        val backend = BackendConnection(this, info)
        backend.connect {
            deferred.complete(false)
        }
        
        return deferred.await()
    }
}
