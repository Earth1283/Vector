package dev.vector.proxy.network.session

import dev.vector.api.event.PlayerLeaveEvent
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.SessionHandler
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ClientPlaySessionHandler(private val player: VectorPlayer) : SessionHandler {

    private val logger = LoggerFactory.getLogger(ClientPlaySessionHandler::class.java)

    override fun handleUnknown(buf: ByteBuf) {
        val backend = player.currentBackendConn ?: return
        if (backend.channel.isActive) {
            buf.retain()
            backend.channel.writeAndFlush(buf)
        }
    }

    override fun disconnected() {
        logger.info("{} disconnected", player.username)
        player.server.playerDisconnected(player.uuid)
        player.currentBackendConn?.close()
        player.server.proxyScope.launch {
            player.server.eventBus.fire(PlayerLeaveEvent(player))
        }
    }

    override fun exception(cause: Throwable) {
        logger.debug("Client error for {}: {}", player.username, cause.message)
        player.connection.close()
    }
}
