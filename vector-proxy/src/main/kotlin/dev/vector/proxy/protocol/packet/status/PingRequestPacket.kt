package dev.vector.proxy.protocol.packet.status

import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import io.netty.buffer.ByteBuf

class PingRequestPacket(var payload: Long = 0) : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) { payload = buf.readLong() }
    override fun encode(buf: ByteBuf, version: ProtocolVersion) { buf.writeLong(payload) }
}
