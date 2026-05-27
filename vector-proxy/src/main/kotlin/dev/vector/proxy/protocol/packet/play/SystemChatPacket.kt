package dev.vector.proxy.protocol.packet.play

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.writeString
import io.netty.buffer.ByteBuf

class SystemChatPacket(var message: String = "", var overlay: Boolean = false) : MinecraftPacket {

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        message = buf.readString()
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_19.protocol) {
            overlay = buf.readBoolean()
        } else {
            buf.readByte() // position, discard
            if (version.protocol >= ProtocolVersion.MINECRAFT_1_16.protocol) {
                buf.readLong() // sender UUID high, discard
                buf.readLong() // sender UUID low, discard
            }
        }
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeString(message)
        when {
            version.protocol >= ProtocolVersion.MINECRAFT_1_19.protocol ->
                buf.writeBoolean(overlay)
            version.protocol >= ProtocolVersion.MINECRAFT_1_16.protocol -> {
                buf.writeByte(1)   // position: 1 = system message
                buf.writeLong(0L)  // sender UUID: nil (high bits)
                buf.writeLong(0L)  // sender UUID: nil (low bits)
            }
            else ->
                buf.writeByte(1)   // position: 1 = system message
        }
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
