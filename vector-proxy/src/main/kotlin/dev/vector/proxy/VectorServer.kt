package dev.vector.proxy

import dev.vector.api.ProxyServer
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.proxy.config.VectorConfig
import dev.vector.proxy.console.ProxyConsole
import dev.vector.proxy.crypto.CryptoUtils
import dev.vector.proxy.event.EventBusImpl
import dev.vector.proxy.model.BackendServerInfo
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.MinecraftFrameEncoder
import dev.vector.proxy.network.MinecraftPacketDecoder
import dev.vector.proxy.network.MinecraftPacketEncoder
import dev.vector.proxy.network.MinecraftVarintFrameDecoder
import dev.vector.proxy.network.session.HandshakeSessionHandler
import dev.vector.proxy.network.NettyTransport
import dev.vector.proxy.plugin.PluginManager
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import java.security.KeyPair
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VectorServer(val config: VectorConfig, val console: ProxyConsole? = null) : ProxyServer {

    private val logger = LoggerFactory.getLogger(VectorServer::class.java)

    override val version: String = "1.0.0-SNAPSHOT"

    val keyPair: KeyPair

    private val _players = ConcurrentHashMap<UUID, VectorPlayer>()
    override val players: Collection<dev.vector.api.VectorPlayer> get() = _players.values

    val servers: Map<String, BackendServerInfo>
    private val disabledServers = ConcurrentHashMap.newKeySet<String>()

    override val eventBus = EventBusImpl()
    val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var pluginManager: PluginManager
        private set

    private var startTime: Instant = Instant.now()

    init {
        console?.argumentProvider = ::consoleArgumentProvider

        logger.info("Generating RSA key pair...")
        keyPair = CryptoUtils.generateKeyPair()
        logger.info("RSA key pair ready (2048-bit RSA)")

        servers = config.servers.mapValues { (name, addr) ->
            val colon = addr.lastIndexOf(':')
            val (host, port) = if (colon != -1) {
                addr.substring(0, colon) to addr.substring(colon + 1).toInt()
            } else {
                addr to 25565
            }
            BackendServerInfo(name, InetSocketAddress(host, port))
        }

        if (servers.isEmpty()) {
            logger.warn("No backend servers configured — players will be unable to connect")
        } else {
            logger.info("Configured {} backend server(s):", servers.size)
            servers.forEach { (name, info) ->
                val tag = if (config.routing.tryServers.contains(name)) " [default]" else ""
                logger.info("  {} → {}:{}{}", name, info.address.hostString, info.address.port, tag)
            }
        }

        logger.info("Forwarding mode: {}", config.forwarding.mode.name.lowercase())
        logger.info("Compression threshold: {} bytes", config.compression.threshold)
    }

    override fun getPlayer(uuid: UUID): dev.vector.api.VectorPlayer? = _players[uuid]
    override fun getPlayer(username: String): dev.vector.api.VectorPlayer? =
        _players.values.find { it.username == username }

    fun playerConnected(player: VectorPlayer) {
        _players[player.uuid] = player
        logger.info("{} ({}) connected [{} online]", player.username, player.uuid, _players.size)
    }

    fun playerDisconnected(uuid: UUID) {
        _players.remove(uuid)?.let { player ->
            logger.info("{} ({}) disconnected [{} online]", player.username, uuid, _players.size - 1)
        }
    }

    fun getServer(name: String): BackendServerInfo? = servers[name]

    fun getInitialServer(): BackendServerInfo? =
        config.routing.tryServers.firstNotNullOfOrNull { name ->
            servers[name]?.takeIf { name !in disabledServers }
        }

    // -- Console commands ------------------------------------------------------

    fun consoleArgumentProvider(command: String, argIndex: Int): List<String> = when (command) {
        "kick" -> when (argIndex) {
            0    -> players.map { it.username } + listOf("all")
            else -> emptyList()
        }
        "servers" -> when (argIndex) {
            0    -> listOf("probe", "--probe", "-p")
            else -> emptyList()
        }
        "serverctl" -> when (argIndex) {
            0    -> listOf("list", "status", "enable", "disable")
            1    -> servers.keys.toList()
            else -> emptyList()
        }
        else -> emptyList()
    }

    suspend fun handleConsoleCommand(line: String) {
        val parts = line.trim().split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim()

        when (cmd) {
            "stop"      -> cmdStop()
            "players"   -> cmdPlayers()
            "plugins"   -> cmdPlugins()
            "servers"   -> cmdServers(arg)
            "serverctl" -> cmdServerctl(arg)
            "version"   -> cmdVersion()
            "uptime"    -> cmdUptime()
            "kick"      -> cmdKick(arg)
            "broadcast" -> cmdBroadcast(arg)
            "help"      -> cmdHelp()
            else -> logger.warn("Unknown command '{}' — type 'help' for a list", cmd)
        }
    }

    private fun cmdStop() {
        logger.info("Stopping proxy (online: {})...", _players.size)
        _players.values.forEach { it.disconnect("Proxy shutting down") }
        proxyScope.coroutineContext[Job]?.cancel()
        System.exit(0)
    }

    private fun cmdPlayers() {
        val list = players.toList()
        if (list.isEmpty()) {
            logger.info("No players online.")
            return
        }
        logger.info("{} player(s) online:", list.size)
        list.forEach { p ->
            val impl = p as? VectorPlayer
            val srv = impl?.currentServerInfo?.name ?: "?"
            logger.info("  {} | {} | server={}", p.username, p.uuid, srv)
        }
    }

    private fun cmdPlugins() {
        val loaded = if (::pluginManager.isInitialized) pluginManager.plugins else emptyList()
        if (loaded.isEmpty()) {
            logger.info("No plugins loaded.")
            return
        }
        logger.info("{} plugin(s) loaded:", loaded.size)
        loaded.forEach { c ->
            logger.info("  {} v{}  ({})", c.manifest.name, c.manifest.version, c.manifest.id)
        }
    }

    private suspend fun cmdServers(arg: String?) {
        if (servers.isEmpty()) {
            logger.info("No backend servers configured.")
            return
        }

        val probe = arg?.lowercase() in listOf("probe", "--probe", "-p")

        if (!probe) {
            logger.info("{} backend server(s):", servers.size)
            servers.forEach { (name, info) ->
                val count = _players.values.count { (it as? VectorPlayer)?.currentServerInfo?.name == name }
                val tag = if (config.routing.tryServers.contains(name)) " [default]" else ""
                logger.info("  {} → {}:{}  ({} player(s)){}",
                    name, info.address.hostString, info.address.port, count, tag)
            }
            return
        }

        logger.info("Probing {} backend server(s)...", servers.size)

        data class ProbeResult(val online: Boolean, val latencyMs: Long)

        val results: Map<String, ProbeResult> = coroutineScope {
            servers.map { (name, info) ->
                name to async(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    val up = runCatching {
                        Socket().use { it.connect(info.address, PROBE_TIMEOUT_MS) }
                    }.isSuccess
                    ProbeResult(up, System.currentTimeMillis() - t0)
                }
            }.associate { (name, deferred) -> name to deferred.await() }
        }

        results.forEach { (name, result) ->
            val info = servers[name]!!
            val count = _players.values.count { (it as? VectorPlayer)?.currentServerInfo?.name == name }
            val tag   = if (config.routing.tryServers.contains(name)) " [default]" else ""

            val (dotStyle, statusText) = if (result.online)
                AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.GREEN) to "online  ${result.latencyMs}ms"
            else
                AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.RED)   to "offline"

            val line = AttributedStringBuilder()
                .style(dotStyle).append("  ● ")
                .style(AttributedStyle.DEFAULT).append(name)
                .style(AttributedStyle.DEFAULT.faint()).append(" → ${info.address.hostString}:${info.address.port}")
                .style(AttributedStyle.DEFAULT.faint()).append("  ($count online$tag)  ")
                .style(dotStyle).append("[$statusText]")
                .toAnsi()

            console?.printAbove(line) ?: logger.info(line)
        }
    }

    private suspend fun cmdServerctl(arg: String?) {
        val parts = arg?.trim()?.split(" ", limit = 2) ?: emptyList()
        val sub   = parts.getOrNull(0)?.lowercase()
        val name  = parts.getOrNull(1)?.trim()

        when (sub) {
            null, "list" -> {
                if (servers.isEmpty()) { logger.info("No units loaded."); return }
                val header = "  %-16s %-24s %7s  %s".format("UNIT", "ADDRESS", "PLAYERS", "LOAD")
                logger.info(header)
                servers.forEach { (n, info) ->
                    val count   = _players.values.count { (it as? VectorPlayer)?.currentServerInfo?.name == n }
                    val isRoute = config.routing.tryServers.contains(n)
                    val load    = if (n in disabledServers) "disabled" else if (isRoute) "enabled (default)" else "enabled"
                    val dot     = if (n in disabledServers) "○" else "●"
                    logger.info("  {} %-16s %-24s %7s  %s".format(
                        dot, "$n.service", "${info.address.hostString}:${info.address.port}", "$count online", load))
                }
            }

            "status" -> {
                if (name == null) { logger.warn("Usage: serverctl status <server>"); return }
                val info = servers[name]
                if (info == null) { logger.warn("Unknown server '{}'", name); return }

                val count   = _players.values.count { (it as? VectorPlayer)?.currentServerInfo?.name == name }
                val isRoute = config.routing.tryServers.contains(name)
                val load    = if (name in disabledServers) "disabled" else if (isRoute) "enabled; preset default" else "enabled"

                val t0 = System.currentTimeMillis()
                val up = runCatching {
                    java.net.Socket().use { it.connect(info.address, PROBE_TIMEOUT_MS) }
                }.isSuccess
                val latencyMs = System.currentTimeMillis() - t0

                val (dotStyle, activeLine) = if (up)
                    AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.GREEN) to "active (running)  ${latencyMs}ms"
                else
                    AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.RED)   to "inactive (dead)"

                fun print(text: String) = console?.printAbove(text) ?: logger.info(text)

                val dotLine = AttributedStringBuilder()
                    .style(dotStyle).append("● ")
                    .style(AttributedStyle.DEFAULT.bold()).append("$name.service")
                    .style(AttributedStyle.DEFAULT.faint()).append(" — ${info.address.hostString}:${info.address.port}")
                    .toAnsi()
                print(dotLine)

                val activeFmt = AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.faint()).append("   Active:  ")
                    .style(dotStyle).append(activeLine)
                    .toAnsi()
                print(activeFmt)

                print("   Players: $count online")
                print("   Load:    $load")
            }

            "enable" -> {
                if (name == null) { logger.warn("Usage: serverctl enable <server>"); return }
                if (!servers.containsKey(name)) { logger.warn("Unknown server '{}'", name); return }
                if (disabledServers.remove(name)) {
                    logger.info("Synchronizing state for $name.service...")
                    logger.info("$name.service: enabled")
                } else {
                    logger.info("Unit $name.service is already enabled.")
                }
            }

            "disable" -> {
                if (name == null) { logger.warn("Usage: serverctl disable <server>"); return }
                if (!servers.containsKey(name)) { logger.warn("Unknown server '{}'", name); return }
                if (disabledServers.add(name)) {
                    logger.info("Removed $name.service from routing pool.")
                    logger.info("$name.service: disabled")
                } else {
                    logger.info("Unit $name.service is already disabled.")
                }
            }

            else -> {
                logger.warn("Unknown subcommand '{}'. Available: list, status, enable, disable", sub)
            }
        }
    }

    companion object {
        private const val PROBE_TIMEOUT_MS = 2_000
    }

    private fun cmdVersion() {
        logger.info("Vector {} (Minecraft proxy)", version)
        logger.info("Java {} — {}", System.getProperty("java.version"), System.getProperty("java.vendor"))
        logger.info("OS   {} {}", System.getProperty("os.name"), System.getProperty("os.arch"))
        logger.info("Transport: {}", when {
            NettyTransport.isEpoll  -> "Epoll (Linux native)"
            NettyTransport.isKQueue -> "KQueue (macOS native)"
            else                    -> "NIO (portable)"
        })
    }

    private fun cmdUptime() {
        val d = Duration.between(startTime, Instant.now())
        val days    = d.toDays()
        val hours   = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        val seconds = d.seconds % 60
        val fmt = buildString {
            if (days > 0)    append("${days}d ")
            if (hours > 0 || days > 0)  append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${seconds}s")
        }
        logger.info("Uptime: {}", fmt)
    }

    private fun cmdKick(arg: String?) {
        if (arg.isNullOrBlank()) {
            logger.warn("Usage: kick <player|all> [reason]")
            return
        }
        val kickParts = arg.split(" ", limit = 2)
        val target = kickParts[0]
        val reason = kickParts.getOrNull(1) ?: "Kicked by operator"

        if (target.equals("all", ignoreCase = true)) {
            val list = players.toList()
            if (list.isEmpty()) { logger.info("No players to kick."); return }
            list.forEach { it.disconnect(reason) }
            logger.info("Kicked {} player(s): {}", list.size, reason)
            return
        }

        val player = getPlayer(target)
        if (player == null) {
            logger.warn("Player '{}' is not online", target)
            return
        }
        player.disconnect(reason)
        logger.info("Kicked {} ({})", player.username, reason)
    }

    private fun cmdBroadcast(arg: String?) {
        if (arg.isNullOrBlank()) {
            logger.warn("Usage: broadcast <message>")
            return
        }
        // Chat packet broadcasting requires Play-phase packet support (Part 7.3).
        // For now, log it so ops can see it; it won't reach players in-game.
        logger.info("[Broadcast] {}", arg)
        logger.warn("In-game broadcast not yet implemented (requires Part 7.3 chat packets)")
    }

    private fun cmdHelp() {
        logger.info("Available commands:")
        logger.info("  players                              — list online players")
        logger.info("  kick <player|all> [reason]           — disconnect a player")
        logger.info("  servers [probe|--probe|-p]           — list backends; probe pings each one")
        logger.info("  serverctl list                       — list backend units (systemctl style)")
        logger.info("  serverctl status <server>            — probe and detail a single backend")
        logger.info("  serverctl enable|disable <server>    — add/remove backend from routing pool")
        logger.info("  plugins                              — list loaded plugins")
        logger.info("  broadcast <message>                  — broadcast a message (stub)")
        logger.info("  version                              — show version and runtime info")
        logger.info("  uptime                               — show how long the proxy has been running")
        logger.info("  stop                                 — shut down the proxy gracefully")
    }

    // -- Lifecycle -------------------------------------------------------------

    fun start() {
        startTime = Instant.now()

        val folderJob = proxyScope.launch {
            val pluginsDir = Paths.get("plugins")
            if (!pluginsDir.exists()) {
                try {
                    pluginsDir.createDirectories()
                } catch (e: Exception) {
                    logger.error("Failed to create plugins/ directory: {}", e.message)
                }
            }
        }

        val bindAddr = run {
            val colon = config.bind.lastIndexOf(':')
            val (host, port) = if (colon != -1) {
                config.bind.substring(0, colon) to config.bind.substring(colon + 1).toInt()
            } else {
                config.bind to 25565
            }
            InetSocketAddress(host, port)
        }

        logger.info("Binding on {}...", config.bind)

        val boss = NettyTransport.createEventLoopGroup(1)
        val worker = NettyTransport.createEventLoopGroup()
        try {
            val bound = ServerBootstrap()
                .group(boss, worker)
                .channel(NettyTransport.serverChannelClass)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val conn = MinecraftConnection(ch, this@VectorServer)
                        ch.pipeline()
                            .addLast("frame-decoder", MinecraftVarintFrameDecoder())
                            .addLast("packet-decoder", MinecraftPacketDecoder())
                            .addLast("packet-encoder", MinecraftPacketEncoder())
                            .addLast("frame-encoder", MinecraftFrameEncoder())
                            .addLast("handler", conn)
                        conn.setSessionHandler(HandshakeSessionHandler(conn))
                    }
                })
                .bind(bindAddr)
                .sync()

            logger.info("Proxy bound on {} ✓", config.bind)

            pluginManager = PluginManager(this)
            runBlocking {
                folderJob.join()
                pluginManager.loadPlugins(Paths.get("plugins"))
                eventBus.fire(ProxyInitializeEvent())
            }

            val pluginCount = pluginManager.plugins.size
            if (pluginCount == 0) {
                logger.info("No plugins found in plugins/")
            } else {
                logger.info("Loaded {} plugin(s)", pluginCount)
            }

            val elapsed = Duration.between(startTime, Instant.now())
            val elapsedStr = "${elapsed.seconds}.${(elapsed.nano / 1_000_000).toString().padStart(3, '0')}s"
            logger.info("Vector is ready ({}) — Type 'help' for commands.", elapsedStr)

            bound.channel().closeFuture().sync()
        } finally {
            boss.shutdownGracefully()
            worker.shutdownGracefully()
        }
    }
}
