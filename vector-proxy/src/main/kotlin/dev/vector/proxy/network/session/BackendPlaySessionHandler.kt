package dev.vector.proxy.network.session

import dev.vector.api.event.ServerSwitchEvent
import dev.vector.proxy.config.VectorConfig.BackendDisconnectAction
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.BackendConnection
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.packet.play.PlayDisconnectPacket
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class BackendPlaySessionHandler(private val player: VectorPlayer) : SessionHandler {

    private val logger = LoggerFactory.getLogger(BackendPlaySessionHandler::class.java)

    override fun handleUnknown(buf: ByteBuf) {
        if (player.connection.channel.isActive) {
            buf.retain()
            player.connection.channel.write(buf, player.connection.channel.voidPromise())
        }
    }

    override fun disconnected() {
        if (player.connection.isClosed) return
        val cfg = player.server.config.playerExperience.backendDisconnect
        logger.debug("Backend disconnected for {} (action={})", player.username, cfg.action)

        when (cfg.action) {
            BackendDisconnectAction.SEND_TO_FALLBACK -> {
                val previousServer = player.currentServerInfo
                val fallback = player.server.config.routing.tryServers
                    .filter { it != previousServer?.name }
                    .firstNotNullOfOrNull { player.server.serverMap[it] }

                if (fallback != null) {
                    player.connection.setAutoReading(false)
                    player.currentBackendConn = null
                    player.currentServerInfo = null
                    player.server.proxyScope.launch {
                        player.server.eventBus.fire(ServerSwitchEvent(player, previousServer, fallback))
                    }
                    val newBackend = BackendConnection(player, fallback)
                    newBackend.connect {
                        player.connection.closeWith(PlayDisconnectPacket(buildDisconnectJson(cfg.fallbackMessage)))
                    }
                } else {
                    player.connection.closeWith(PlayDisconnectPacket(buildDisconnectJson(cfg.fallbackMessage)))
                }
            }
            BackendDisconnectAction.KICK -> {
                player.connection.closeWith(PlayDisconnectPacket(buildDisconnectJson(cfg.fallbackMessage)))
            }
        }
    }

    override fun exception(cause: Throwable) {
        logger.debug("Backend error for {}: {}", player.username, cause.message)
        player.connection.close()
    }

    private fun buildDisconnectJson(message: String): String {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"text":"$escaped"}"""
    }
}
