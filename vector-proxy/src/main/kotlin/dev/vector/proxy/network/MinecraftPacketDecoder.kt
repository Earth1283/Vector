package dev.vector.proxy.network

import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.StateRegistry
import dev.vector.proxy.protocol.util.readVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder

class MinecraftPacketDecoder : MessageToMessageDecoder<ByteBuf>() {
    @Volatile var state: ProtocolState = ProtocolState.HANDSHAKING
    @Volatile var protocolVersion: ProtocolVersion = ProtocolVersion.UNKNOWN

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        val packetId = buf.readVarInt()
        // Before handshake the version is UNKNOWN; fall back to MINIMUM so the registry floor lookup works.
        val effectiveVersion = if (protocolVersion == ProtocolVersion.UNKNOWN)
            ProtocolVersion.MINIMUM else protocolVersion

        val factory = StateRegistry.serverbound(state).getFactory(packetId, effectiveVersion) ?: return

        val packet = factory()
        packet.decode(buf, effectiveVersion)
        out.add(packet)
    }
}
