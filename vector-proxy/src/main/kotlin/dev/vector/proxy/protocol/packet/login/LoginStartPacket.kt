package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.readUUID
import dev.vector.proxy.protocol.util.writeString
import dev.vector.proxy.protocol.util.writeUUID
import io.netty.buffer.ByteBuf
import java.util.UUID

class LoginStartPacket : MinecraftPacket {
    var username: String = ""
    var uuid: UUID? = null

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        username = buf.readString(16)
        when {
            // 1.19.3+ sends UUID directly after username
            version.protocol >= ProtocolVersion.MINECRAFT_1_19_3.protocol -> {
                uuid = buf.readUUID()
            }
            // 1.19 / 1.19.2 appended chat signing fields we don't need
            else -> buf.skipBytes(buf.readableBytes())
        }
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeString(username)
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_19_3.protocol) {
            buf.writeUUID(uuid ?: UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()))
        }
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
