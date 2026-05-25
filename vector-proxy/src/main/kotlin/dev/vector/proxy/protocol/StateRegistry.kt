package dev.vector.proxy.protocol

import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_7_2
import dev.vector.proxy.protocol.packet.handshake.HandshakePacket
import dev.vector.proxy.protocol.packet.login.EncryptionRequestPacket
import dev.vector.proxy.protocol.packet.login.EncryptionResponsePacket
import dev.vector.proxy.protocol.packet.login.LoginDisconnectPacket
import dev.vector.proxy.protocol.packet.login.LoginStartPacket
import dev.vector.proxy.protocol.packet.login.LoginSuccessPacket
import dev.vector.proxy.protocol.packet.status.PingRequestPacket
import dev.vector.proxy.protocol.packet.status.PingResponsePacket
import dev.vector.proxy.protocol.packet.status.StatusRequestPacket
import dev.vector.proxy.protocol.packet.status.StatusResponsePacket

object StateRegistry {
    private val serverboundRegistries = mapOf(
        ProtocolState.HANDSHAKING to DirectionRegistry().apply {
            register(HandshakePacket::class, MINECRAFT_1_7_2, 0x00) { HandshakePacket() }
        },
        ProtocolState.STATUS to DirectionRegistry().apply {
            register(StatusRequestPacket::class, MINECRAFT_1_7_2, 0x00) { StatusRequestPacket() }
            register(PingRequestPacket::class, MINECRAFT_1_7_2, 0x01) { PingRequestPacket() }
        },
        ProtocolState.LOGIN to DirectionRegistry().apply {
            register(LoginStartPacket::class,        MINECRAFT_1_7_2, 0x00) { LoginStartPacket() }
            register(EncryptionResponsePacket::class, MINECRAFT_1_7_2, 0x01) { EncryptionResponsePacket() }
        },
    )

    private val clientboundRegistries = mapOf(
        ProtocolState.STATUS to DirectionRegistry().apply {
            register(StatusResponsePacket::class, MINECRAFT_1_7_2, 0x00) { StatusResponsePacket() }
            register(PingResponsePacket::class,   MINECRAFT_1_7_2, 0x01) { PingResponsePacket() }
        },
        ProtocolState.LOGIN to DirectionRegistry().apply {
            register(LoginDisconnectPacket::class,   MINECRAFT_1_7_2, 0x00) { LoginDisconnectPacket() }
            register(LoginSuccessPacket::class,      MINECRAFT_1_7_2, 0x02) { LoginSuccessPacket() }
            register(EncryptionRequestPacket::class, MINECRAFT_1_7_2, 0x01) { EncryptionRequestPacket() }
        },
    )

    fun serverbound(state: ProtocolState): DirectionRegistry =
        serverboundRegistries[state] ?: error("No serverbound registry for $state")

    fun clientbound(state: ProtocolState): DirectionRegistry =
        clientboundRegistries[state] ?: error("No clientbound registry for $state")
}
