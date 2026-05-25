package dev.vector.proxy.network.session

import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.BackendConnection
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.login.LoginAcknowledgedPacket
import dev.vector.proxy.protocol.packet.login.LoginDisconnectPacket
import dev.vector.proxy.protocol.packet.login.LoginSuccessPacket
import org.slf4j.LoggerFactory

class ClientLoginSuccessSessionHandler(
    private val connection: MinecraftConnection,
    private val player: VectorPlayer,
    private val backendConnection: BackendConnection,
) : SessionHandler {

    private val logger = LoggerFactory.getLogger(ClientLoginSuccessSessionHandler::class.java)

    override fun activated() {
        connection.write(LoginSuccessPacket.from(player.profile, connection.protocolVersion))

        if (connection.protocolVersion.protocol < ProtocolVersion.MINECRAFT_1_20_2.protocol) {
            // Pre-1.20.2: no LoginAcknowledged — connect to backend immediately.
            connectToBackend()
        }
        // 1.20.2+: wait for LoginAcknowledged in handle() below.
    }

    override fun handle(packet: LoginAcknowledgedPacket): Boolean {
        connectToBackend()
        return true
    }

    override fun exception(cause: Throwable) {
        logger.debug("Post-login error from {}: {}", connection.remoteAddress, cause.message)
        connection.close()
    }

    private fun connectToBackend() {
        backendConnection.connect {
            connection.closeWith(
                LoginDisconnectPacket("""{"text":"Could not connect to backend server","color":"red"}""")
            )
        }
    }
}
