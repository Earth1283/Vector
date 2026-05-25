package dev.vector.proxy.protocol.packet.handshake

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readString
import dev.vector.proxy.protocol.util.readVarInt
import dev.vector.proxy.protocol.util.writeString
import dev.vector.proxy.protocol.util.writeVarInt
import io.netty.buffer.ByteBuf

class HandshakePacket : MinecraftPacket {
    var protocolVersion: Int = 0
    var serverAddress: String = ""
    var serverPort: Int = 0
    var nextState: Int = 0

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        protocolVersion = buf.readVarInt()
        serverAddress = buf.readString(255)
        serverPort = buf.readUnsignedShort()
        nextState = buf.readVarInt()
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeVarInt(protocolVersion)
        buf.writeString(serverAddress)
        buf.writeShort(serverPort)
        buf.writeVarInt(nextState)
    }

    override fun handle(handler: SessionHandler) = handler.handle(this)
}
