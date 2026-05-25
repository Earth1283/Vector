package dev.vector.proxy.network

import dev.vector.proxy.protocol.Direction
import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.StateRegistry
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class MinecraftPacketEncoder(
    private val direction: Direction = Direction.CLIENTBOUND,
) : MessageToByteEncoder<MinecraftPacket>() {
    @Volatile var state: ProtocolState = ProtocolState.HANDSHAKING
    @Volatile var protocolVersion: ProtocolVersion = ProtocolVersion.UNKNOWN

    override fun encode(ctx: ChannelHandlerContext, msg: MinecraftPacket, out: ByteBuf) {
        val effectiveVersion = if (protocolVersion == ProtocolVersion.UNKNOWN)
            ProtocolVersion.MINIMUM else protocolVersion

        val registry = if (direction == Direction.CLIENTBOUND) StateRegistry.clientbound(state)
                       else StateRegistry.serverbound(state)
        val packetId = registry.getPacketId(msg::class, effectiveVersion)
            ?: throw IllegalStateException(
                "No packet ID for ${msg::class.simpleName} in state=$state version=${effectiveVersion.versionString}"
            )

        out.writeVarInt(packetId)
        msg.encode(out, effectiveVersion)
    }
}
