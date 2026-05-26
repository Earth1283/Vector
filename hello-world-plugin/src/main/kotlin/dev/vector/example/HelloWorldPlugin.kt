package dev.vector.example

import dev.vector.api.event.EventPriority
import dev.vector.api.event.PlayerJoinEvent
import dev.vector.api.event.PlayerLeaveEvent
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.api.kotlin.VectorPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class HelloWorldPlugin : VectorPlugin({

    // Shared plugin state — lives for the proxy lifetime once enable() runs.
    val sessions = ConcurrentHashMap<UUID, Instant>()
    val totalJoins = AtomicLong(0)

    // ── Lifecycle ────────────────────────────────────────────────────────────

    onEnable {
        logger.info("Hello from Vector! Running proxy v{}", server.version)
        logger.info("{} player(s) online at startup", server.players.size)
    }

    // ── Join handling ────────────────────────────────────────────────────────

    on<PlayerJoinEvent> { event ->
        val player = event.player
        sessions[player.uuid] = Instant.now()
        val count = totalJoins.incrementAndGet()
        logger.info("{} joined the proxy ({} online, {} total joins)",
            player.username, server.players.size, count)

        // Demonstrate coroutine usage: log a greeting 2 s after the player
        // enters play state so any early packets have settled.
        launch {
            delay(2.seconds)
            // In a real plugin this would be sendMessage(); for now we log.
            logger.info("Welcome, {}! You are player #{} on this proxy.",
                player.username, count)
        }
    }

    // MONITOR priority — runs after all other handlers including cancelled ones.
    // Useful for auditing what the final handler chain decided.
    on<PlayerJoinEvent>(priority = EventPriority.MONITOR) { event ->
        logger.debug("[monitor] join pipeline complete for {}", event.player.username)
    }

    // ── Leave handling ───────────────────────────────────────────────────────

    on<PlayerLeaveEvent> { event ->
        val player = event.player
        val joinedAt = sessions.remove(player.uuid)
        val online = server.players.size  // already decremented by the time we hear this
        if (joinedAt != null) {
            val d = Duration.between(joinedAt, Instant.now())
            val formatted = "%dm %02ds".format(d.toMinutes(), d.seconds % 60)
            logger.info("{} left after {} ({} still online)", player.username, formatted, online)
        } else {
            logger.info("{} left ({} still online)", player.username, online)
        }
    }
})
