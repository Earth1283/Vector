package dev.vector.proxy.network

import dev.vector.proxy.protocol.packet.MinecraftPacket
import dev.vector.proxy.protocol.packet.configuration.ConfigurationDisconnectPacket
import dev.vector.proxy.protocol.packet.handshake.HandshakePacket
import dev.vector.proxy.protocol.packet.login.EncryptionRequestPacket
import dev.vector.proxy.protocol.packet.login.EncryptionResponsePacket
import dev.vector.proxy.protocol.packet.login.LoginAcknowledgedPacket
import dev.vector.proxy.protocol.packet.login.LoginDisconnectPacket
import dev.vector.proxy.protocol.packet.login.LoginPluginMessagePacket
import dev.vector.proxy.protocol.packet.login.LoginPluginResponsePacket
import dev.vector.proxy.protocol.packet.login.LoginStartPacket
import dev.vector.proxy.protocol.packet.login.LoginSuccessPacket
import dev.vector.proxy.protocol.packet.login.SetCompressionPacket
import dev.vector.proxy.protocol.packet.play.PlayDisconnectPacket
import dev.vector.proxy.protocol.packet.play.SystemChatPacket
import dev.vector.proxy.protocol.packet.status.PingRequestPacket
import dev.vector.proxy.protocol.packet.status.PingResponsePacket
import dev.vector.proxy.protocol.packet.status.StatusRequestPacket
import dev.vector.proxy.protocol.packet.status.StatusResponsePacket
import io.netty.buffer.ByteBuf

interface SessionHandler {
    // Lifecycle 
    fun connected()   {}
    fun disconnected() {}
    fun activated()   {}
    fun deactivated() {}
    fun exception(cause: Throwable) {}

    // Generic fallbacks 
    fun handleGeneric(packet: MinecraftPacket) {}
    fun handleUnknown(buf: ByteBuf) {}

    // Handshake 
    fun handle(packet: HandshakePacket): Boolean = false

    // Status 
    fun handle(packet: StatusRequestPacket): Boolean = false
    fun handle(packet: StatusResponsePacket): Boolean = false
    fun handle(packet: PingRequestPacket): Boolean = false
    fun handle(packet: PingResponsePacket): Boolean = false

    // Login (serverbound) 
    fun handle(packet: LoginStartPacket): Boolean = false
    fun handle(packet: EncryptionResponsePacket): Boolean = false
    fun handle(packet: LoginAcknowledgedPacket): Boolean = false

    // Login (clientbound) 
    fun handle(packet: EncryptionRequestPacket): Boolean = false
    fun handle(packet: LoginSuccessPacket): Boolean = false
    fun handle(packet: LoginDisconnectPacket): Boolean = false
    fun handle(packet: SetCompressionPacket): Boolean = false
    fun handle(packet: LoginPluginMessagePacket): Boolean = false

    // Login plugin (serverbound) 
    fun handle(packet: LoginPluginResponsePacket): Boolean = false

    // Configuration (clientbound) 
    fun handle(packet: ConfigurationDisconnectPacket): Boolean = false

    // Play (clientbound) 
    fun handle(packet: PlayDisconnectPacket): Boolean = false
    fun handle(packet: SystemChatPacket): Boolean = false
}
