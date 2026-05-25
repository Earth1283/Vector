package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readByteArray
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.writeByteArray
import dev.vector.proxy.protocol.util.writeString
import io.netty.buffer.ByteBuf

class EncryptionRequestPacket(
    var serverId: String = "",
    var publicKey: ByteArray = ByteArray(0),
    var verifyToken: ByteArray = ByteArray(0),
) : MinecraftPacket {
    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        serverId = buf.readString(20)
        publicKey = buf.readByteArray(512)
        verifyToken = buf.readByteArray(16)
        // 1.20.5+ appends a shouldAuthenticate boolean — skip it on decode (server never decodes this)
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_20_5.protocol && buf.isReadable) {
            buf.skipBytes(buf.readableBytes())
        }
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeString(serverId)
        buf.writeByteArray(publicKey)
        buf.writeByteArray(verifyToken)
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_20_5.protocol) {
            buf.writeBoolean(true) // shouldAuthenticate — always true
        }
    }
}
