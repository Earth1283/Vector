package dev.vector.proxy.network

import dev.vector.proxy.VectorServer
import dev.vector.proxy.crypto.CryptoUtils
import dev.vector.proxy.protocol.Direction
import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import io.netty.buffer.ByteBuf
import org.slf4j.LoggerFactory
import java.net.SocketAddress
import javax.crypto.Cipher

class MinecraftConnection(val channel: Channel, val server: VectorServer? = null) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(MinecraftConnection::class.java)

    var protocolVersion: ProtocolVersion = ProtocolVersion.UNKNOWN
        private set
    var state: ProtocolState = ProtocolState.HANDSHAKING
        private set
    var virtualHost: String = ""

    @Volatile var playerState: PlayerState = PlayerState.Handshaking
        private set

    private var sessionHandler: SessionHandler? = null
    private var knownDisconnect = false

    val isClosed: Boolean get() = !channel.isActive
    val remoteAddress: SocketAddress get() = channel.remoteAddress()

    // -- Netty callbacks -------------------------------------------------------

    override fun channelActive(ctx: ChannelHandlerContext) {
        sessionHandler?.connected()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        transitionState(PlayerState.Disconnecting)
        sessionHandler?.disconnected()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        try {
            val handler = sessionHandler ?: return
            when (msg) {
                is MinecraftPacket -> if (!msg.handle(handler)) handler.handleGeneric(msg)
                is ByteBuf         -> handler.handleUnknown(msg)
                else               -> {}
            }
        } finally {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val handler = sessionHandler
        if (handler != null) {
            handler.exception(cause)
        } else {
            logger.debug("Unhandled exception from {}: {}", remoteAddress, cause.message)
            ctx.close()
        }
    }

    // -- Session handler -------------------------------------------------------

    fun setSessionHandler(handler: SessionHandler) {
        sessionHandler?.deactivated()
        sessionHandler = handler
        handler.activated()
    }

    fun transitionState(next: PlayerState) {
        if (playerState is PlayerState.Disconnecting) return
        playerState = playerState.transition(next)
    }

    // -- State / protocol version ----------------------------------------------

    fun setState(next: ProtocolState) {
        state = next
        channel.pipeline().get(MinecraftPacketDecoder::class.java)?.state = next
        channel.pipeline().get(MinecraftPacketEncoder::class.java)?.state = next
    }

    fun setProtocolVersion(version: ProtocolVersion) {
        protocolVersion = version
        channel.pipeline().get(MinecraftPacketDecoder::class.java)?.protocolVersion = version
        channel.pipeline().get(MinecraftPacketEncoder::class.java)?.protocolVersion = version
    }

    // -- Pipeline mutations ----------------------------------------------------

    fun enableEncryption(sharedSecret: ByteArray) {
        val dec = CryptoUtils.createCipher(Cipher.DECRYPT_MODE, sharedSecret)
        val enc = CryptoUtils.createCipher(Cipher.ENCRYPT_MODE, sharedSecret)
        channel.pipeline()
            .addFirst("cipher-decoder", MinecraftCipherDecoder(dec))
            .addFirst("cipher-encoder", MinecraftCipherEncoder(enc))
    }

    fun enableCompression(threshold: Int) {
        channel.pipeline().addAfter("frame-decoder", "compress-decoder", MinecraftCompressDecoder(threshold))
        channel.pipeline().replace("frame-encoder", "compress-encoder", MinecraftCompressEncoder(threshold))
    }

    // -- Write helpers ---------------------------------------------------------

    fun write(packet: MinecraftPacket) {
        if (channel.isActive) channel.writeAndFlush(packet)
    }

    fun delayedWrite(packet: MinecraftPacket) {
        if (channel.isActive) channel.write(packet, channel.voidPromise())
    }

    fun flush() {
        if (channel.isActive) channel.flush()
    }

    fun closeWith(packet: MinecraftPacket) {
        if (channel.isActive) {
            knownDisconnect = true
            channel.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE)
        }
    }

    fun close() {
        if (channel.isActive) {
            knownDisconnect = true
            if (channel.eventLoop().inEventLoop()) channel.close()
            else channel.eventLoop().execute { channel.close() }
        }
    }

    // -- AutoRead --------------------------------------------------------------

    fun setAutoReading(autoReading: Boolean) {
        channel.config().isAutoRead = autoReading
        if (autoReading) channel.read()
    }
}
