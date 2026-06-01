package dev.vector.proxy.network.session

import dev.vector.proxy.config.VectorConfig.ForwardingMode
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.BackendConnection
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.PlayerDataForwarding
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.handshake.HandshakePacket
import dev.vector.proxy.protocol.packet.login.EncryptionRequestPacket
import dev.vector.proxy.protocol.packet.login.LoginAcknowledgedPacket
import dev.vector.proxy.protocol.packet.login.LoginDisconnectPacket
import dev.vector.proxy.protocol.packet.login.LoginPluginMessagePacket
import dev.vector.proxy.protocol.packet.login.LoginPluginResponsePacket
import dev.vector.proxy.protocol.packet.login.LoginStartPacket
import dev.vector.proxy.protocol.packet.login.LoginSuccessPacket
import dev.vector.proxy.protocol.packet.login.SetCompressionPacket
import dev.vector.api.event.PlayerJoinEvent
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class BackendLoginSessionHandler(
    private val backendConn: MinecraftConnection,
    private val backendConnection: BackendConnection,
    private val protocolVersion: ProtocolVersion,
) : SessionHandler {

    private val logger = LoggerFactory.getLogger(BackendLoginSessionHandler::class.java)
    private val player: VectorPlayer get() = backendConnection.player
    private var informationForwarded = false

    override fun connected() {
        val config = player.server.config
        val serverHost = backendConnection.serverInfo.address.hostString
        val serverPort = backendConnection.serverInfo.address.port
        val playerIp = playerIp()

        val handshakeAddress = when (config.forwarding.mode) {
            ForwardingMode.LEGACY -> PlayerDataForwarding.createLegacyForwardingAddress(
                serverHost, playerIp, player.profile
            )
            ForwardingMode.BUNGEEGUARD -> PlayerDataForwarding.createBungeeGuardForwardingAddress(
                serverHost, playerIp, player.profile,
                config.forwarding.secret.toByteArray(Charsets.UTF_8)
            )
            else -> serverHost
        }

        val handshake = HandshakePacket().apply {
            this.protocolVersion = this@BackendLoginSessionHandler.protocolVersion.protocol
            this.serverAddress = handshakeAddress
            this.serverPort = serverPort
            this.nextState = 2
        }
        backendConn.delayedWrite(handshake)

        val loginStart = LoginStartPacket().apply {
            username = player.profile.username
            uuid = player.profile.uuid
        }
        backendConn.write(loginStart)

        backendConn.setState(ProtocolState.LOGIN)
        backendConn.setProtocolVersion(protocolVersion)
    }

    override fun handle(packet: SetCompressionPacket): Boolean {
        backendConn.enableCompression(packet.threshold)
        return true
    }

    override fun handle(packet: LoginPluginMessagePacket): Boolean {
        val config = player.server.config
        if (config.forwarding.mode == ForwardingMode.MODERN
            && packet.channel == PlayerDataForwarding.CHANNEL
        ) {
            val requestedVersion = if (packet.data.size == 1)
                packet.data[0].toInt() and 0xFF
            else
                PlayerDataForwarding.MODERN_DEFAULT

            val data = PlayerDataForwarding.createModernForwardingData(
                secret = config.forwarding.secret.toByteArray(Charsets.UTF_8),
                playerIp = playerIp(),
                profile = player.profile,
                requestedVersion = requestedVersion,
            )
            backendConn.write(LoginPluginResponsePacket(packet.messageId, true, data))
            informationForwarded = true
        } else {
            backendConn.write(LoginPluginResponsePacket(packet.messageId, false, ByteArray(0)))
        }
        return true
    }

    override fun handle(packet: LoginSuccessPacket): Boolean {
        val config = player.server.config
        if (config.forwarding.mode == ForwardingMode.MODERN && !informationForwarded) {
            logger.warn(
                "Backend did not request modern forwarding for {}. " +
                "Is velocity-native-forwarding enabled on the backend?", player.username
            )
        }
        if (protocolVersion.protocol >= ProtocolVersion.MINECRAFT_1_20_2.protocol) {
            backendConn.write(LoginAcknowledgedPacket())
        }
        swapToForwarding()
        return true
    }

    override fun handle(packet: LoginDisconnectPacket): Boolean {
        logger.warn("Backend rejected login for {}: {}", player.username, packet.reason)
        player.server.playerDisconnected(player.uuid)
        // packet.reason is raw JSON from the backend — forward it directly rather than
        // string-interpolating it, which would allow a malicious backend to inject JSON.
        player.connection.closeWith(LoginDisconnectPacket(packet.reason))
        backendConn.close()
        return true
    }

    override fun handle(packet: EncryptionRequestPacket): Boolean {
        logger.warn("Backend requested encryption — only offline-mode backends are supported")
        player.server.playerDisconnected(player.uuid)
        player.connection.closeWith(
            LoginDisconnectPacket("""{"text":"Backend is in online mode (unsupported)","color":"red"}""")
        )
        backendConn.close()
        return true
    }

    override fun exception(cause: Throwable) {
        logger.warn("Backend connection error for {}: {}", player.username, cause.message)
        player.server.playerDisconnected(player.uuid)
        player.connection.close()
        backendConn.close()
    }

    private fun swapToForwarding() {
        logger.info("Forwarding {} <-> {}", player.connection.remoteAddress, backendConnection.serverInfo.name)

        player.connection.transitionState(PlayerState.InServer(backendConnection.serverInfo.name))
        player.currentServerInfo = backendConnection.serverInfo
        player.currentBackendConn = backendConn

        // Remove client-side decoder; raw ByteBuf forwarding takes over for inbound.
        // Guard against second call (server switch) where decoder is already gone.
        // Keep packet-encoder so structured packets (e.g. PlayDisconnect) can still be sent.
        if (player.connection.channel.pipeline().get("packet-decoder") != null) {
            player.connection.channel.pipeline().remove("packet-decoder")
        }
        // Advance encoder state to PLAY so it resolves Play-phase packet IDs correctly.
        player.connection.setState(ProtocolState.PLAY)

        backendConn.channel.pipeline().remove("packet-decoder")
        backendConn.channel.pipeline().remove("packet-encoder")

        player.connection.setSessionHandler(ClientPlaySessionHandler(player))
        backendConn.setSessionHandler(BackendPlaySessionHandler(player))

        player.connection.setAutoReading(true)

        player.pendingConnection?.complete(true)
        player.pendingConnection = null

        player.server.proxyScope.launch {
            player.server.eventBus.fire(PlayerJoinEvent(player))
        }
    }

    private fun playerIp(): String {
        val addr = player.connection.remoteAddress
        return if (addr is InetSocketAddress) addr.address.hostAddress else addr.toString()
    }
}
