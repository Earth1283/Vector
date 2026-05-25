package dev.vector.proxy.protocol.packet

import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolVersion
import io.netty.buffer.ByteBuf

interface MinecraftPacket {
    fun decode(buf: ByteBuf, version: ProtocolVersion)
    fun encode(buf: ByteBuf, version: ProtocolVersion)
    fun handle(handler: SessionHandler): Boolean = false
}
