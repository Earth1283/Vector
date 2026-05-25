package dev.vector.proxy.network

import dev.vector.proxy.crypto.CryptoUtils
import dev.vector.proxy.crypto.MojangAuth
import dev.vector.proxy.model.GameProfile
import dev.vector.proxy.protocol.ProtocolState
import dev.vector.proxy.protocol.ProtocolVersion
import dev.vector.proxy.protocol.packet.MinecraftPacket
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
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.security.SecureRandom
import javax.crypto.Cipher

class InitialHandler(private val keyPair: KeyPair) : SimpleChannelInboundHandler<MinecraftPacket>() {
    private val logger = LoggerFactory.getLogger(InitialHandler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var protocolVersion: ProtocolVersion = ProtocolVersion.UNKNOWN
    private var loginUsername: String = ""
    private var verifyToken: ByteArray = ByteArray(0)

    override fun channelActive(ctx: ChannelHandlerContext) {
        logger.info("Connection from {}", ctx.channel().remoteAddress())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        scope.cancel()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: MinecraftPacket) {
        when (msg) {
            is HandshakePacket         -> handleHandshake(ctx, msg)
            is StatusRequestPacket     -> handleStatusRequest(ctx)
            is PingRequestPacket       -> handlePing(ctx, msg)
            is LoginStartPacket        -> handleLoginStart(ctx, msg)
            is EncryptionResponsePacket -> handleEncryptionResponse(ctx, msg)
            else -> ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.debug("Connection error from {}: {}", ctx.channel().remoteAddress(), cause.message)
        ctx.close()
    }

    // ── Handshake / Status ────────────────────────────────────────────────────

    private fun handleHandshake(ctx: ChannelHandlerContext, packet: HandshakePacket) {
        protocolVersion = ProtocolVersion.fromProtocol(packet.protocolVersion)
        when (packet.nextState) {
            1 -> transitionTo(ctx, ProtocolState.STATUS)
            2 -> transitionTo(ctx, ProtocolState.LOGIN)
            else -> ctx.close()
        }
    }

    private fun handleStatusRequest(ctx: ChannelHandlerContext) {
        val version = protocolVersion.takeUnless { it == ProtocolVersion.UNKNOWN } ?: ProtocolVersion.MAXIMUM
        ctx.writeAndFlush(StatusResponsePacket(buildStatusJson(version)))
    }

    private fun handlePing(ctx: ChannelHandlerContext, packet: PingRequestPacket) {
        ctx.writeAndFlush(PingResponsePacket(packet.payload)).addListener(ChannelFutureListener.CLOSE)
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    private fun handleLoginStart(ctx: ChannelHandlerContext, packet: LoginStartPacket) {
        loginUsername = packet.username
        logger.info("Login attempt: {} ({})", loginUsername, ctx.channel().remoteAddress())

        verifyToken = ByteArray(4).also { SecureRandom().nextBytes(it) }

        ctx.writeAndFlush(
            EncryptionRequestPacket(
                serverId = "",
                publicKey = keyPair.public.encoded,
                verifyToken = verifyToken,
            )
        )
    }

    private fun handleEncryptionResponse(ctx: ChannelHandlerContext, packet: EncryptionResponsePacket) {
        if (packet.hasSignature) {
            disconnect(ctx, """{"text":"Chat signing is not supported","color":"red"}""")
            return
        }

        val sharedSecret = runCatching {
            CryptoUtils.decryptRsa(keyPair.private, packet.sharedSecret)
        }.getOrElse {
            logger.warn("Failed to decrypt shared secret from {}", ctx.channel().remoteAddress())
            ctx.close(); return
        }

        val decryptedToken = runCatching {
            CryptoUtils.decryptRsa(keyPair.private, packet.verifyToken!!)
        }.getOrElse {
            logger.warn("Failed to decrypt verify token from {}", ctx.channel().remoteAddress())
            ctx.close(); return
        }

        if (!decryptedToken.contentEquals(verifyToken)) {
            logger.warn("Verify token mismatch from {}", ctx.channel().remoteAddress())
            ctx.close(); return
        }

        // Enable cipher in both directions immediately — the client starts encrypting now.
        enableCipher(ctx, sharedSecret)

        val serverHash = CryptoUtils.serverHash("", sharedSecret, keyPair.public.encoded)

        scope.launch {
            val profile = MojangAuth.verify(loginUsername, serverHash)
            if (profile == null) {
                ctx.channel().eventLoop().execute {
                    disconnect(ctx, """{"text":"Authentication failed. Are you logged in?","color":"red"}""")
                }
                return@launch
            }

            ctx.channel().eventLoop().execute {
                onAuthenticated(ctx, profile)
            }
        }
    }

    private fun onAuthenticated(ctx: ChannelHandlerContext, profile: GameProfile) {
        logger.info("Authenticated: {} ({})", profile.username, profile.uuid)
        // Send LoginSuccess then close — Part 5 replaces the close with backend forwarding.
        ctx.writeAndFlush(LoginSuccessPacket.from(profile, protocolVersion))
            .addListener(ChannelFutureListener.CLOSE)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun transitionTo(ctx: ChannelHandlerContext, state: ProtocolState) {
        ctx.pipeline().get(MinecraftPacketDecoder::class.java).also {
            it.state = state
            it.protocolVersion = protocolVersion
        }
        ctx.pipeline().get(MinecraftPacketEncoder::class.java).also {
            it.state = state
            it.protocolVersion = protocolVersion
        }
    }

    private fun enableCipher(ctx: ChannelHandlerContext, sharedSecret: ByteArray) {
        val decCipher = CryptoUtils.createCipher(Cipher.DECRYPT_MODE, sharedSecret)
        val encCipher = CryptoUtils.createCipher(Cipher.ENCRYPT_MODE, sharedSecret)
        // addFirst twice: results in [cipher-encoder, cipher-decoder, frame-decoder, ...]
        // Inbound:  cipher-decoder decrypts → frame-decoder frames → packet-decoder decodes
        // Outbound: packet-encoder encodes → cipher-encoder encrypts → channel
        ctx.pipeline()
            .addFirst("cipher-decoder", MinecraftCipherDecoder(decCipher))
            .addFirst("cipher-encoder", MinecraftCipherEncoder(encCipher))
    }

    private fun disconnect(ctx: ChannelHandlerContext, reason: String) {
        ctx.writeAndFlush(LoginDisconnectPacket(reason)).addListener(ChannelFutureListener.CLOSE)
    }

    private fun buildStatusJson(version: ProtocolVersion) =
        """{"version":{"name":"${version.versionString}","protocol":${version.protocol}},"players":{"max":100,"online":0,"sample":[]},"description":{"text":"A Vector Proxy"}}"""
}
