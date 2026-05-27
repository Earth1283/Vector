package dev.vector.proxy.protocol

import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_7_2
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_9
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_13
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_15
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_16
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_17
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_19
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_19_1
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_19_3
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_19_4
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_20_2
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_20_3
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_20_5
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_21
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_21_2
import dev.vector.proxy.protocol.ProtocolVersion.MINECRAFT_1_21_4
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
            register(LoginStartPacket::class,           MINECRAFT_1_7_2, 0x00) { LoginStartPacket() }
            register(EncryptionResponsePacket::class,   MINECRAFT_1_7_2, 0x01) { EncryptionResponsePacket() }
            register(LoginPluginResponsePacket::class,  MINECRAFT_1_7_2, 0x02) { LoginPluginResponsePacket() }
            register(LoginAcknowledgedPacket::class,    MINECRAFT_1_7_2, 0x03) { LoginAcknowledgedPacket() }
        },
        ProtocolState.CONFIGURATION to DirectionRegistry(),
        ProtocolState.PLAY to DirectionRegistry()
    )

    private val clientboundRegistries = mapOf(
        ProtocolState.STATUS to DirectionRegistry().apply {
            register(StatusResponsePacket::class, MINECRAFT_1_7_2, 0x00) { StatusResponsePacket() }
            register(PingResponsePacket::class,   MINECRAFT_1_7_2, 0x01) { PingResponsePacket() }
        },
        ProtocolState.LOGIN to DirectionRegistry().apply {
            register(LoginDisconnectPacket::class,    MINECRAFT_1_7_2, 0x00) { LoginDisconnectPacket() }
            register(EncryptionRequestPacket::class,  MINECRAFT_1_7_2, 0x01) { EncryptionRequestPacket() }
            register(LoginSuccessPacket::class,       MINECRAFT_1_7_2, 0x02) { LoginSuccessPacket() }
            register(SetCompressionPacket::class,     MINECRAFT_1_7_2, 0x03) { SetCompressionPacket() }
            register(LoginPluginMessagePacket::class, MINECRAFT_1_7_2, 0x04) { LoginPluginMessagePacket() }
        },
        ProtocolState.CONFIGURATION to DirectionRegistry().apply {
            register(ConfigurationDisconnectPacket::class, MINECRAFT_1_20_2, 0x02) { ConfigurationDisconnectPacket() }
        },
        ProtocolState.PLAY to DirectionRegistry().apply {
            register(PlayDisconnectPacket::class, MINECRAFT_1_7_2,  0x40) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_9,    0x1A) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_13,   0x1B) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_16,   0x19) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_17,   0x1A) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_19,   0x17) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_19_4, 0x1A) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_20_2, 0x1B) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_20_5, 0x1D) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_21,   0x1B) { PlayDisconnectPacket() }
            register(PlayDisconnectPacket::class, MINECRAFT_1_21_4, 0x1A) { PlayDisconnectPacket() }
            // System chat / legacy chat (broadcast, console messages)
            register(SystemChatPacket::class, MINECRAFT_1_7_2,  0x02) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_9,    0x0F) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_13,   0x0E) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_15,   0x0F) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_16,   0x0E) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_17,   0x0F) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_19,   0x5F) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_19_1, 0x62) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_19_3, 0x60) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_19_4, 0x64) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_20_2, 0x67) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_20_3, 0x69) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_20_5, 0x6C) { SystemChatPacket() }
            register(SystemChatPacket::class, MINECRAFT_1_21_2, 0x73) { SystemChatPacket() }
        }
    )

    fun serverbound(state: ProtocolState): DirectionRegistry =
        serverboundRegistries[state] ?: error("No serverbound registry for $state")

    fun clientbound(state: ProtocolState): DirectionRegistry =
        clientboundRegistries[state] ?: error("No clientbound registry for $state")
}
