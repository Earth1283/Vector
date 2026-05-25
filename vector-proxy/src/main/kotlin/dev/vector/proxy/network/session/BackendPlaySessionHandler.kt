package dev.vector.proxy.network.session

import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.SessionHandler
import io.netty.buffer.ByteBuf
import org.slf4j.LoggerFactory

class BackendPlaySessionHandler(private val player: VectorPlayer) : SessionHandler {

    private val logger = LoggerFactory.getLogger(BackendPlaySessionHandler::class.java)

    override fun handleUnknown(buf: ByteBuf) {
        if (player.connection.channel.isActive) {
            buf.retain()
            player.connection.channel.writeAndFlush(buf)
        }
    }

    override fun disconnected() {
        logger.debug("Backend disconnected for {}", player.username)
        player.connection.close()
    }

    override fun exception(cause: Throwable) {
        logger.debug("Backend error for {}: {}", player.username, cause.message)
        player.connection.close()
    }
}
