package dev.vector.proxy.network

import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.StateRegistry
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class MinecraftPacketEncoder : MessageToByteEncoder<MinecraftPacket>() {
    @Volatile var state: ProtocolState = ProtocolState.HANDSHAKING
    @Volatile var protocolVersion: ProtocolVersion = ProtocolVersion.UNKNOWN

    override fun encode(ctx: ChannelHandlerContext, msg: MinecraftPacket, out: ByteBuf) {
        val effectiveVersion = if (protocolVersion == ProtocolVersion.UNKNOWN)
            ProtocolVersion.MINIMUM else protocolVersion

        val packetId = StateRegistry.clientbound(state).getPacketId(msg::class, effectiveVersion)
            ?: throw IllegalStateException(
                "No packet ID for ${msg::class.simpleName} in state=$state version=${effectiveVersion.versionString}"
            )

        val packetBuf = ctx.alloc().buffer()
        try {
            packetBuf.writeVarInt(packetId)
            msg.encode(packetBuf, effectiveVersion)
            out.writeVarInt(packetBuf.readableBytes())
            out.writeBytes(packetBuf)
        } finally {
            packetBuf.release()
        }
    }
}
