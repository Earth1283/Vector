package dev.vector.proxy.model

import dev.vector.proxy.VectorServer
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.play.PlayDisconnectPacket
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
    var currentBackendConn: MinecraftConnection? = null

    override fun disconnect(reason: String) {
        if (connection.playerState is PlayerState.InServer) {
            val escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"")
            connection.closeWith(PlayDisconnectPacket("""{"text":"$escaped"}"""))
        } else {
            connection.close()
        }
    }
}
