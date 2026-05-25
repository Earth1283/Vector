package dev.vector.proxy.protocol.packet.status

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.writeString
import io.netty.buffer.ByteBuf

class StatusResponsePacket(var json: String = "") : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        json = buf.readString(Short.MAX_VALUE.toInt())
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeString(json)
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
