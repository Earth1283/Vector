package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import io.netty.buffer.ByteBuf

class LoginAcknowledgedPacket : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) {}
    override fun encode(buf: ByteBuf, version: ProtocolVersion) {}
    override fun handle(handler: SessionHandler) = handler.handle(this)
}
