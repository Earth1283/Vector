package dev.vector.proxy.protocol.packet.status

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import io.netty.buffer.ByteBuf

class StatusRequestPacket : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) = Unit
    override fun encode(buf: ByteBuf, version: ProtocolVersion) = Unit
    override fun handle(handler: SessionHandler) = handler.handle(this)
}
