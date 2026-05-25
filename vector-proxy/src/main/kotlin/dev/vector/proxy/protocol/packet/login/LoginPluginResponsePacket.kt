package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readVarInt
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf

class LoginPluginResponsePacket(
    var messageId: Int = 0,
    var success: Boolean = false,
    var data: ByteArray = ByteArray(0),
) : MinecraftPacket {

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        messageId = buf.readVarInt()
        success = buf.readBoolean()
        data = if (buf.isReadable) ByteArray(buf.readableBytes()).also { buf.readBytes(it) } else ByteArray(0)
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeVarInt(messageId)
        buf.writeBoolean(success)
        buf.writeBytes(data)
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
