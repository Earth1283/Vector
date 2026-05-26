package dev.vector.proxy.network.session

import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.handshake.HandshakePacket

class HandshakeSessionHandler(
    private val connection: MinecraftConnection,
) : SessionHandler {

    override fun handle(packet: HandshakePacket): Boolean {
        connection.setProtocolVersion(ProtocolVersion.fromProtocol(packet.protocolVersion))
        when (packet.nextState) {
            1 -> {
                connection.setState(ProtocolState.STATUS)
                connection.setSessionHandler(StatusSessionHandler(connection))
            }
            2 -> {
                connection.transitionState(PlayerState.LoggingIn)
                connection.setState(ProtocolState.LOGIN)
                connection.setSessionHandler(LoginSessionHandler(connection))
            }
            else -> connection.close()
        }
        return true
    }

    override fun exception(cause: Throwable) {
        connection.close()
    }
}
