package dev.vector.proxy.network.session

import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.BackendConnection
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.network.SessionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ClientLimboSessionHandler(private val player: VectorPlayer) : SessionHandler {

    private val logger = LoggerFactory.getLogger(ClientLimboSessionHandler::class.java)
    private val limboConfig = player.server.config.limbo
    private val entryTimeMs = System.currentTimeMillis()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun activated() {
        player.connection.transitionState(PlayerState.Limbo)
        logger.info("{} placed in limbo (max hold: {}s)", player.username, limboConfig.maxHoldDuration)
        scheduleRetry()
    }

    override fun deactivated() {
        scope.cancel()
    }

    private fun scheduleRetry() {
        scope.launch {
            delay(RETRY_INTERVAL_MS)
            player.connection.channel.eventLoop().execute {
                if (player.connection.isClosed) return@execute
                val elapsed = (System.currentTimeMillis() - entryTimeMs) / 1000L
                if (limboConfig.maxHoldDuration > 0 && elapsed >= limboConfig.maxHoldDuration) {
                    logger.info("{} limbo hold expired, disconnecting", player.username)
                    player.server.playerDisconnected(player.uuid)
                    player.connection.close()
                    return@execute
                }
                val server = player.server.getInitialServer(player.connection.virtualHost)
                if (server == null) {
                    scheduleRetry()
                    return@execute
                }
                val backend = BackendConnection(player, server)
                backend.connect { scheduleRetry() }
            }
        }
    }

    companion object {
        private const val RETRY_INTERVAL_MS = 5_000L
    }
}
