package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.readVarInt
import dev.vector.proxy.protocol.util.writeString
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf

class LoginPluginMessagePacket : MinecraftPacket {
    var messageId: Int = 0
    var channel: String = ""
    var data: ByteArray = ByteArray(0)

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        messageId = buf.readVarInt()
        channel = buf.readString()
        data = if (buf.isReadable) ByteArray(buf.readableBytes()).also { buf.readBytes(it) } else ByteArray(0)
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeVarInt(messageId)
        buf.writeString(channel)
        buf.writeBytes(data)
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
