package dev.vector.proxy.network.session

import dev.vector.proxy.config.VectorConfig
import dev.vector.proxy.crypto.MojangAuth
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.BackendConnection
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.packet.login.LoginDisconnectPacket
import dev.vector.proxy.protocol.packet.login.SetCompressionPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class AuthSessionHandler(
    private val connection: MinecraftConnection,
    private val username: String,
    private val serverHash: String,
) : SessionHandler {

    private val logger = LoggerFactory.getLogger(AuthSessionHandler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun activated() {
        connection.transitionState(PlayerState.Authenticating)
        scope.launch {
            val profile = MojangAuth.verify(username, serverHash)
            connection.channel.eventLoop().execute {
                // Guard: client may have disconnected while the Mojang HTTP call was in-flight.
                // Without this check, playerConnected() would add a ghost player that is never
                // removed because channelInactive already fired on the previous handler.
                if (connection.isClosed) return@execute

                if (profile == null) {
                    connection.closeWith(
                        LoginDisconnectPacket("""{"text":"Authentication failed. Are you logged in?","color":"red"}""")
                    )
                    return@execute
                }

                val server = connection.server!!
                val player = VectorPlayer(profile, connection, server)
                val initialServer = server.getInitialServer(connection.virtualHost)

                val threshold = server.config.compression.threshold
                if (threshold >= 0) {
                    connection.write(SetCompressionPacket(threshold))
                    connection.enableCompression(threshold)
                }

                if (initialServer == null) {
                    val limbo = server.config.limbo
                    if (limbo.unclaimedAction == VectorConfig.LimboAction.HOLD) {
                        server.playerConnected(player)
                        connection.setSessionHandler(ClientLoginSuccessSessionHandler(connection, player, null))
                    } else {
                        connection.closeWith(
                            LoginDisconnectPacket("""{"text":"No backend servers are configured","color":"red"}""")
                        )
                    }
                    return@execute
                }

                server.playerConnected(player)

                val backendConnection = BackendConnection(player, initialServer)
                connection.setSessionHandler(
                    ClientLoginSuccessSessionHandler(connection, player, backendConnection)
                )
            }
        }
    }

    override fun disconnected() {
        scope.cancel()
    }

    override fun exception(cause: Throwable) {
        logger.debug("Auth error from {}: {}", connection.remoteAddress, cause.message)
        scope.cancel()
        connection.close()
    }
}
