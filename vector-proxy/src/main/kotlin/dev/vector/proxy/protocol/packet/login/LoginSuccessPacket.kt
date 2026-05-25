package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.model.GameProfile
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.readUUID
import dev.vector.proxy.protocol.util.readVarInt
import dev.vector.proxy.protocol.util.writeString
import dev.vector.proxy.protocol.util.writeUUID
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf
import java.util.UUID

class LoginSuccessPacket(
    var uuid: UUID = UUID(0, 0),
    var username: String = "",
    var properties: List<PropertyEntry> = emptyList(),
) : MinecraftPacket {

    data class PropertyEntry(val name: String, val value: String, val signature: String? = null)

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        uuid = if (version.protocol >= ProtocolVersion.MINECRAFT_1_16.protocol) {
            buf.readUUID()
        } else {
            UUID.fromString(buf.readString(36))
        }
        username = buf.readString(16)
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_19_3.protocol && buf.isReadable) {
            val count = buf.readVarInt()
            properties = List(count) {
                val name = buf.readString()
                val value = buf.readString()
                val signature = if (buf.readBoolean()) buf.readString() else null
                PropertyEntry(name, value, signature)
            }
        }
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_16.protocol) {
            buf.writeUUID(uuid)
        } else {
            buf.writeString(uuid.toString())
        }
        buf.writeString(username)
        if (version.protocol >= ProtocolVersion.MINECRAFT_1_19_3.protocol) {
            buf.writeVarInt(properties.size)
            for (prop in properties) {
                buf.writeString(prop.name)
                buf.writeString(prop.value)
                buf.writeBoolean(prop.signature != null)
                prop.signature?.let { buf.writeString(it) }
            }
        }
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)

    companion object {
        fun from(profile: GameProfile, version: ProtocolVersion) = LoginSuccessPacket(
            uuid = profile.uuid,
            username = profile.username,
            properties = profile.properties.map {
                PropertyEntry(it.name, it.value, it.signature)
            },
        )
    }
}
