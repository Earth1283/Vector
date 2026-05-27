package dev.vector.proxy.network.session

import dev.vector.proxy.config.VectorConfig
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.BackendConnection
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.configuration.ConfigurationDisconnectPacket
import dev.vector.proxy.protocol.packet.login.LoginAcknowledgedPacket
import dev.vector.proxy.protocol.packet.login.LoginSuccessPacket
import dev.vector.proxy.protocol.packet.play.PlayDisconnectPacket
import org.slf4j.LoggerFactory

class ClientLoginSuccessSessionHandler(
    private val connection: MinecraftConnection,
    private val player: VectorPlayer,
    private val backendConnection: BackendConnection?,
) : SessionHandler {

    private val logger = LoggerFactory.getLogger(ClientLoginSuccessSessionHandler::class.java)

    override fun activated() {
        connection.write(LoginSuccessPacket.from(player.profile, connection.protocolVersion))

        if (connection.protocolVersion.protocol < ProtocolVersion.MINECRAFT_1_20_2.protocol) {
            connectOrLimbo()
        }
        // 1.20.2+: wait for LoginAcknowledged.
    }

    override fun handle(packet: LoginAcknowledgedPacket): Boolean {
        connectOrLimbo()
        return true
    }

    override fun exception(cause: Throwable) {
        logger.debug("Post-login error from {}: {}", connection.remoteAddress, cause.message)
        connection.close()
    }

    private fun connectOrLimbo() {
        val backend = backendConnection
        if (backend == null) {
            connection.setSessionHandler(ClientLimboSessionHandler(player))
            return
        }
        backend.connect {
            val limboAction = player.server.config.limbo.unclaimedAction
            if (limboAction == VectorConfig.LimboAction.HOLD) {
                connection.setSessionHandler(ClientLimboSessionHandler(player))
            } else {
                kickClient("Could not connect to backend server")
            }
        }
    }

    private fun kickClient(message: String) {
        if (connection.protocolVersion.protocol >= ProtocolVersion.MINECRAFT_1_20_2.protocol) {
            connection.setState(ProtocolState.CONFIGURATION)
            connection.closeWith(ConfigurationDisconnectPacket("""{"text":"$message","color":"red"}"""))
        } else {
            connection.setState(ProtocolState.PLAY)
            connection.closeWith(PlayDisconnectPacket("""{"text":"$message","color":"red"}"""))
        }
    }
}
