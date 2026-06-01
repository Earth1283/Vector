package dev.vector.proxy.network.session

import dev.vector.proxy.crypto.CryptoUtils
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.SessionHandler
import dev.vector.proxy.protocol.packet.login.EncryptionRequestPacket
import dev.vector.proxy.protocol.packet.login.EncryptionResponsePacket
import dev.vector.proxy.protocol.packet.login.LoginDisconnectPacket
import dev.vector.proxy.protocol.packet.login.LoginStartPacket
import org.slf4j.LoggerFactory
import java.security.SecureRandom

class LoginSessionHandler(
    private val connection: MinecraftConnection,
) : SessionHandler {

    private val logger = LoggerFactory.getLogger(LoginSessionHandler::class.java)
    private var loginUsername = ""
    private var verifyToken = ByteArray(0)

    companion object {
        private val SECURE_RANDOM = SecureRandom()
    }

    override fun connected() {
        logger.info("Connection from {}", connection.remoteAddress)
    }

    override fun handle(packet: LoginStartPacket): Boolean {
        loginUsername = packet.username

        val server = connection.server!!
        val maintenance = server.config.management.maintenance
        if (maintenance.enabled) {
            val msg = maintenance.message.let { m ->
                if (m.trimStart().startsWith("{")) m
                else """{"text":"${m.replace("\\", "\\\\").replace("\"", "\\\"")}","color":"red"}"""
            }
            logger.info("Rejected {} — maintenance mode", loginUsername)
            connection.closeWith(LoginDisconnectPacket(msg))
            return true
        }

        logger.info("Login attempt: {} ({})", loginUsername, connection.remoteAddress)

        val keyPair = server.keyPair
        verifyToken = ByteArray(4).also { SECURE_RANDOM.nextBytes(it) }
        connection.write(EncryptionRequestPacket(
            serverId = "",
            publicKey = keyPair.public.encoded,
            verifyToken = verifyToken,
        ))
        return true
    }

    override fun handle(packet: EncryptionResponsePacket): Boolean {
        if (packet.hasSignature) {
            connection.closeWith(LoginDisconnectPacket("""{"text":"Chat signing is not supported","color":"red"}"""))
            return true
        }

        val keyPair = connection.server!!.keyPair

        val sharedSecret = runCatching {
            CryptoUtils.decryptRsa(keyPair.private, packet.sharedSecret)
        }.getOrElse {
            logger.warn("Failed to decrypt shared secret from {}", connection.remoteAddress)
            connection.close(); return true
        }

        val decryptedToken = runCatching {
            CryptoUtils.decryptRsa(keyPair.private, packet.verifyToken!!)
        }.getOrElse {
            logger.warn("Failed to decrypt verify token from {}", connection.remoteAddress)
            connection.close(); return true
        }

        if (!decryptedToken.contentEquals(verifyToken)) {
            logger.warn("Verify token mismatch from {}", connection.remoteAddress)
            connection.close(); return true
        }

        connection.enableEncryption(sharedSecret)

        val serverHash = CryptoUtils.serverHash("", sharedSecret, keyPair.public.encoded)
        connection.setSessionHandler(AuthSessionHandler(connection, loginUsername, serverHash))
        return true
    }

    override fun exception(cause: Throwable) {
        logger.debug("Login error from {}: {}", connection.remoteAddress, cause.message)
        connection.close()
    }
}
