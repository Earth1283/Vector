package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readVarInt
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf

class SetCompressionPacket(var threshold: Int = -1) : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) { threshold = buf.readVarInt() }
    override fun encode(buf: ByteBuf, version: ProtocolVersion) { buf.writeVarInt(threshold) }
    override fun handle(handler: SessionHandler) = handler.handle(this)
}
