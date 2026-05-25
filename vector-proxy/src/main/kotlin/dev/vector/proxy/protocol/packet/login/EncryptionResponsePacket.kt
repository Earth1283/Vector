package dev.vector.proxy.protocol.packet.login

import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.util.readByteArray
import dev.vector.proxy.protocol.util.writeByteArray
import io.netty.buffer.ByteBuf

class EncryptionResponsePacket : MinecraftPacket {
    var sharedSecret: ByteArray = ByteArray(0)
    var verifyToken: ByteArray? = null
    // True when the client sent a salt+signature instead of a verifyToken (1.19/1.19.1 only).
    // We reject this path — we do not implement chat signing.
    var hasSignature: Boolean = false

    override fun decode(buf: ByteBuf, version: ProtocolVersion) {
        sharedSecret = buf.readByteArray(512)
        // 1.19 and 1.19.1 (protocol 759–760) added a has_verify_token boolean.
        if (version.protocol in 759..760) {
            val hasVerifyToken = buf.readBoolean()
            if (hasVerifyToken) {
                verifyToken = buf.readByteArray(512)
            } else {
                hasSignature = true
                buf.skipBytes(buf.readableBytes())
            }
        } else {
            verifyToken = buf.readByteArray(512)
        }
    }

    override fun encode(buf: ByteBuf, version: ProtocolVersion) {
        buf.writeByteArray(sharedSecret)
        if (version.protocol in 759..760) {
            buf.writeBoolean(true)
        }
        buf.writeByteArray(verifyToken ?: ByteArray(0))
    }
}
