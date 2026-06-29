package dev.vector.proxy.network.session

import dev.vector.api.event.PlayerChatEvent
import dev.vector.api.event.PlayerLeaveEvent
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.readVarInt
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ClientPlaySessionHandler(private val player: VectorPlayer) : SessionHandler {

    private val logger = LoggerFactory.getLogger(ClientPlaySessionHandler::class.java)

    override fun handleUnknown(buf: ByteBuf) {
        if (interceptCommand(buf)) return

        val backend = player.currentBackendConn ?: return
        if (backend.channel.isActive) {
            buf.retain()
            backend.channel.write(buf, backend.channel.voidPromise())
        }
    }

    private fun interceptCommand(buf: ByteBuf): Boolean {
        val readerIndex = buf.readerIndex()
        try {
            val version = player.connection.protocolVersion
            if (version == ProtocolVersion.UNKNOWN) return false

            val packetId = buf.readVarInt()
            val isChat = isChatPacket(packetId, version)
            val isCommand = isCommandPacket(packetId, version)

            if (isChat || isCommand) {
                val message = buf.readString()

                if (isChat && !message.startsWith("/")) {
                    // Non-command chat message — fire PlayerChatEvent and optionally suppress
                    val backend = player.currentBackendConn ?: return false
                    buf.retain()
                    player.server.proxyScope.launch {
                        val event = PlayerChatEvent(player, message)
                        player.server.eventBus.fire(event)
                        if (!event.isCancelled && backend.channel.isActive) {
                            val snapshot = buf.duplicate().readerIndex(readerIndex)
                            backend.channel.write(snapshot.retain(), backend.channel.voidPromise())
                        }
                        buf.release()
                    }
                    return true
                }

                val commandLine = if (isChat) message.substring(1) else message

                val parts = commandLine.trim().split(" ", limit = 2)
                val cmdName = parts[0]
                val arg = parts.getOrNull(1)

                if (player.server.hasCommand(cmdName)) {
                    player.server.proxyScope.launch(dev.vector.compat.currentCommandSender.asContextElement(player.username)) {
                        player.server.handleCommand(cmdName, arg)
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            // Probably not a chat packet or malformed
        } finally {
            buf.readerIndex(readerIndex)
        }
        return false
    }

    private fun isChatPacket(id: Int, version: ProtocolVersion): Boolean {
        return when {
            version.protocol < ProtocolVersion.MINECRAFT_1_19.protocol -> id == 0x01
            version.protocol < ProtocolVersion.MINECRAFT_1_19_1.protocol -> id == 0x03
            version.protocol < ProtocolVersion.MINECRAFT_1_20_2.protocol -> id == 0x05
            version.protocol < ProtocolVersion.MINECRAFT_1_20_5.protocol -> id == 0x06
            else -> id == 0x09
        }
    }

    private fun isCommandPacket(id: Int, version: ProtocolVersion): Boolean {
        return when {
            version.protocol < ProtocolVersion.MINECRAFT_1_19.protocol -> false
            version.protocol < ProtocolVersion.MINECRAFT_1_19_1.protocol -> id == 0x04
            version.protocol < ProtocolVersion.MINECRAFT_1_19_3.protocol -> id == 0x03
            version.protocol < ProtocolVersion.MINECRAFT_1_20_5.protocol -> id == 0x04
            else -> id == 0x08
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
