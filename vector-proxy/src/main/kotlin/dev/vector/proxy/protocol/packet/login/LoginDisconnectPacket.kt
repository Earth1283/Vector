package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.writeString
import io.netty.buffer.ByteBuf

class LoginDisconnectPacket(var reason: String = """{"text":"Disconnected"}""") : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        reason = buf.readString()
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeString(reason)
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
