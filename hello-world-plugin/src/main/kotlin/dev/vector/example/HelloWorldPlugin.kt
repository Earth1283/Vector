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

    val sessions = ConcurrentHashMap<UUID, Instant>()
    val totalJoins = AtomicLong(0)

    // -- Lifecycle ------------------------------------------------------------

    onEnable {
        migrate()  // apply db/migration/V1__init.sql from this JAR
        val lifetime = server.storage.query(
            "SELECT count(*) FROM hello_sessions"
        ) { rs -> rs.getLong(1) }.first()
        logger.info("Hello from Vector! Running proxy v{}", server.version)
        logger.info("{} lifetime session(s) on record.", lifetime)
    }

    onDisable {
        logger.info("HelloWorld shutting down ({} total joins this session).", totalJoins.get())
    }

    // -- Scheduled task -------------------------------------------------------

    every(30.seconds) {
        logger.debug("Heartbeat: {} player(s) online", server.players.size)
    }

    // -- Plugin commands ------------------------------------------------------

    command("hello") { args ->
        val who = args.firstOrNull() ?: "World"
        logger.info("Hello, {}!", who)
    }

    command("greet") { args ->
        val target = args.firstOrNull()
        if (target == null) {
            logger.warn("Usage: greet <player>")
            return@command
        }
        val player = server.getPlayer(target)
        if (player == null) {
            logger.warn("Player '{}' is not online", target)
        } else {
            logger.info("Greeting {} ({})", player.username, player.uuid)
        }
    }

    // -- Join handling --------------------------------------------------------

    on<PlayerJoinEvent> { event ->
        val player = event.player
        sessions[player.uuid] = Instant.now()
        val count = totalJoins.incrementAndGet()
        logger.info("{} joined the proxy ({} online, {} total joins)",
            player.username, server.players.size, count)

        server.storage.execute(
            "INSERT INTO hello_sessions(uuid, username, joined_at) VALUES (?, ?, ?)",
            listOf(player.uuid.toString(), player.username, System.currentTimeMillis()),
        )

        launch {
            delay(2.seconds)
            logger.info("Welcome, {}! You are player #{} on this proxy.",
                player.username, count)
        }
    }

    on<PlayerJoinEvent>(priority = EventPriority.MONITOR) { event ->
        logger.debug("[monitor] join pipeline complete for {}", event.player.username)
    }

    // -- Leave handling -------------------------------------------------------

    on<PlayerLeaveEvent> { event ->
        val player = event.player
        val joinedAt = sessions.remove(player.uuid)
        val online = server.players.size
        if (joinedAt != null) {
            val d = Duration.between(joinedAt, Instant.now())
            val formatted = "%dm %02ds".format(d.toMinutes(), d.seconds % 60)
            logger.info("{} left after {} ({} still online)", player.username, formatted, online)
        } else {
            logger.info("{} left ({} still online)", player.username, online)
        }
    }
})
