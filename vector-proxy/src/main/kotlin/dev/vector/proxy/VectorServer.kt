package dev.vector.proxy

import dev.vector.api.ProxyServer
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.api.storage.StorageBackend
import dev.vector.proxy.storage.SqliteStorageBackend
import dev.vector.proxy.config.VectorConfig
import dev.vector.proxy.console.ProxyConsole
import dev.vector.proxy.crypto.CryptoUtils
import dev.vector.proxy.event.EventBusImpl
import dev.vector.proxy.model.BackendServerInfo
import dev.vector.proxy.model.VectorPlayer
import dev.vector.proxy.network.ConnectionLimiter
import dev.vector.proxy.network.MinecraftConnection
import dev.vector.proxy.network.MinecraftFrameEncoder
import dev.vector.proxy.network.PlayerState
import dev.vector.proxy.network.MinecraftPacketDecoder
import dev.vector.proxy.network.MinecraftPacketEncoder
import dev.vector.proxy.network.MinecraftVarintFrameDecoder
import dev.vector.proxy.network.session.HandshakeSessionHandler
import dev.vector.proxy.network.NettyTransport
import dev.vector.proxy.plugin.PluginManager
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.handler.timeout.ReadTimeoutHandler
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
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Paths
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import java.security.KeyPair
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class VectorServer(val config: VectorConfig, val console: ProxyConsole? = null) : ProxyServer {

    private val logger = LoggerFactory.getLogger(VectorServer::class.java)

    override val version: String = "1.0.0-SNAPSHOT"

    override val storage: StorageBackend = SqliteStorageBackend(config.storage.file)

    val keyPair: KeyPair

    private val _players = ConcurrentHashMap<UUID, VectorPlayer>()
    private val _playersByName = ConcurrentHashMap<String, VectorPlayer>()
    override val players: Collection<dev.vector.api.VectorPlayer> get() = _players.values

    val serverMap = ConcurrentHashMap<String, BackendServerInfo>()
    override val servers: Collection<dev.vector.api.BackendServer> get() = serverMap.values
    private val disabledServers = ConcurrentHashMap.newKeySet<String>()
    private val backendCooldowns = ConcurrentHashMap<String, Long>()
    private val connectionLimiterState = ConnectionLimiter.State()
    
    private data class CommandEntry(
        val pluginId: String,
        val handler: suspend (List<String>) -> Unit,
        val completer: (List<String>) -> List<String>
    )
    private val commandRegistry = ConcurrentHashMap<String, CommandEntry>()

    override val eventBus = EventBusImpl()
    val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val coroutineScope: CoroutineScope get() = proxyScope
    lateinit var pluginManager: PluginManager
        private set

    private var startTime: Instant = Instant.now()

    init {
        console?.argumentProvider = ::consoleArgumentProvider

        logger.info("Generating RSA key pair...")
        keyPair = CryptoUtils.generateKeyPair()
        logger.info("RSA key pair ready")

        config.servers.forEach { (name, addr) ->
            val colon = addr.lastIndexOf(':')
            val (host, port) = if (colon != -1) {
                addr.substring(0, colon) to addr.substring(colon + 1).toInt()
            } else {
                addr to 25565
            }
            serverMap[name] = BackendServerInfo(name, InetSocketAddress(host, port))
        }

        if (serverMap.isEmpty()) {
            logger.warn("No backend servers configured — players will be unable to connect")
        } else {
            logger.info("Configured {} backend server(s):", serverMap.size)
            serverMap.forEach { (name, info) ->
                val tag = if (config.routing.tryServers.contains(name)) " [default]" else ""
                logger.info("  {} → {}:{}{}", name, info.address.hostString, info.address.port, tag)
            }
        }

        logger.info("Forwarding mode: {}", config.forwarding.mode.name.lowercase())

        // Modern and BungeeGuard forwarding both derive their HMAC/token from forwarding.secret.
        // An empty secret means a predictable key, letting anyone who can reach a backend directly
        // forge a player's identity. Refuse to start rather than run insecurely (matches Velocity).
        val fwdMode = config.forwarding.mode
        if ((fwdMode == VectorConfig.ForwardingMode.MODERN || fwdMode == VectorConfig.ForwardingMode.BUNGEEGUARD)
            && config.forwarding.secret.isBlank()
        ) {
            throw IllegalStateException(
                "Forwarding mode '${fwdMode.name.lowercase()}' requires a non-empty 'forwarding.secret'. " +
                "Set a strong random secret shared with your backend servers."
            )
        }

        logger.info("Compression threshold: {} bytes", config.compression.threshold)
        config.connectionLimits.let { cl ->
            logger.info("Connection limits: per-IP={}, total={}",
                if (cl.maxPerIp > 0) cl.maxPerIp.toString() else "unlimited",
                if (cl.maxTotal > 0) cl.maxTotal.toString() else "unlimited")
        }
        logger.info("Storage: SQLite @ {}", config.storage.file)
    }

    override fun getPlayer(uuid: UUID): dev.vector.api.VectorPlayer? = _players[uuid]
    override fun getPlayer(username: String): dev.vector.api.VectorPlayer? =
        _playersByName[username.lowercase()]

    fun playerConnected(player: VectorPlayer) {
        _players[player.uuid] = player
        _playersByName[player.username.lowercase()] = player
        logger.info("{} ({}) connected [{} online]", player.username, player.uuid, _players.size)
    }

    fun playerDisconnected(uuid: UUID) {
        _players.remove(uuid)?.let { player ->
            _playersByName.remove(player.username.lowercase())
            logger.info("{} ({}) disconnected [{} online]", player.username, uuid, _players.size)
        }
    }

    fun getServer(name: String): BackendServerInfo? = serverMap[name]

    fun markBackendUnavailable(name: String) {
        backendCooldowns[name] = System.currentTimeMillis() + BACKEND_COOLDOWN_MS
        logger.debug("Backend {} on cooldown for {}s", name, BACKEND_COOLDOWN_MS / 1000)
    }

    private fun isBackendAvailable(name: String): Boolean {
        val until = backendCooldowns[name] ?: return true
        if (System.currentTimeMillis() >= until) {
            backendCooldowns.remove(name)
            return true
        }
        return false
    }

    fun getInitialServer(virtualHost: String = ""): BackendServerInfo? {
        // Forced-host lookup: strip port and any BungeeCord \0 padding.
        val hostname = virtualHost.substringBefore('\u0000').substringBefore(':').trim().lowercase()
        if (hostname.isNotEmpty()) {
            config.management.forcedHosts[hostname]?.let { serverName ->
                serverMap[serverName]
                    ?.takeIf { serverName !in disabledServers && isBackendAvailable(serverName) }
                    ?.let { return it }
            }
        }
        return config.routing.tryServers.firstNotNullOfOrNull { name ->
            serverMap[name]?.takeIf { name !in disabledServers && isBackendAvailable(name) }
        }
    }

    // MOTD 

    private val faviconData: String? by lazy {
        val file = File(config.motd.favicon)
        if (!file.exists() || file.length() > 64 * 1024) return@lazy null
        "data:image/png;base64," + Base64.getEncoder().encodeToString(file.readBytes())
    }

    fun buildStatusJson(version: dev.vector.proxy.protocol.ProtocolVersion): String {
        val cfg = config.playerExperience
        val rawOnline = _players.size
        val online = max(cfg.playerCountMinimum,
            (rawOnline * cfg.playerCountModifier).roundToInt())
        val displayMax = online + cfg.playerCountMaximumPadding

        val desc = config.motd.description.let { d ->
            if (d.trimStart().startsWith("{")) d
            else """{"text":"${d.escapeJson()}"}"""
        }

        val playersJson = if (cfg.hidePlayerCount) {
            """{"max":-1,"online":-1,"sample":[]}"""
        } else {
            val sample = _players.values.take(12).joinToString(",") { p ->
                """{"name":"${p.username.escapeJson()}","id":"${p.uuid}"}"""
            }
            """{"max":$displayMax,"online":$online,"sample":[$sample]}"""
        }

        val faviconPart = faviconData?.let { ""","favicon":"$it"""" } ?: ""

        return """{"version":{"name":"${version.versionString}","protocol":${version.protocol}},"players":$playersJson,"description":$desc$faviconPart}"""
    }

    private fun String.escapeJson() =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    override fun registerCommand(
        name: String,
        pluginId: String,
        handler: suspend (List<String>) -> Unit,
        completer: (List<String>) -> List<String>
    ) {
        val lowName = name.lowercase()
        commandRegistry[lowName] = CommandEntry(pluginId, handler, completer)
        console?.let { c ->
            if (lowName !in c.builtinCommands) c.builtinCommands = c.builtinCommands + lowName
        }
    }

    override fun unregisterCommand(name: String) {
        val lowName = name.lowercase()
        commandRegistry.remove(lowName)
        console?.let { c -> c.builtinCommands = c.builtinCommands.filter { it != lowName } }
    }

    override fun unregisterCommands(pluginId: String) {
        val removed = commandRegistry.entries.filter { it.value.pluginId == pluginId }.map { it.key }
        removed.forEach { commandRegistry.remove(it) }
        if (removed.isNotEmpty()) {
            console?.let { c -> c.builtinCommands = c.builtinCommands.filter { it !in removed } }
        }
    }

    override fun registerServer(name: String, address: InetSocketAddress): dev.vector.api.BackendServer {
        val info = BackendServerInfo(name, address)
        serverMap[name] = info
        return info
    }

    override fun unregisterServer(name: String) {
        serverMap.remove(name)
        disabledServers.remove(name)
    }

    fun hasCommand(name: String): Boolean = commandRegistry.containsKey(name.lowercase())

    // Console commands 

    fun consoleArgumentProvider(command: String, argIndex: Int): List<String> {
        val line = console?.reader?.buffer?.toString() ?: ""
        val pluginArgs = line.split(" ").drop(1)
        
        commandRegistry[command.lowercase()]?.let { entry ->
            return entry.completer(pluginArgs)
        }

        return when (command) {
            "kick" -> when (argIndex) {
                0    -> players.map { it.username } + listOf("all")
                else -> emptyList()
            }
            "servers" -> when (argIndex) {
                0    -> listOf("probe", "probe", "-p")
                else -> emptyList()
            }
            "serverctl" -> when (argIndex) {
                0    -> listOf("list", "status", "enable", "disable")
                1    -> serverMap.keys.toList()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private val shutdownStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private var bossGroup: io.netty.channel.EventLoopGroup? = null
    private var workerGroup: io.netty.channel.EventLoopGroup? = null
    private var serverChannel: io.netty.channel.Channel? = null

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
            else -> {
                if (!handleCommand(cmd, arg)) {
                    logger.warn("Unknown command '{}' — type 'help' for a list", cmd)
                }
            }
        }
    }

    suspend fun handleCommand(cmd: String, arg: String?): Boolean {
        val entry = commandRegistry[cmd.lowercase()] ?: return false
        val args = arg?.split(" ")?.filter { it.isNotEmpty() } ?: emptyList()
        entry.handler(args)
        return true
    }

    private suspend fun cmdStop() {
        stop()
    }

    fun stop() {
        if (!shutdownStarted.compareAndSet(false, true)) return

        runBlocking {
            logger.info("Stopping proxy (online: {})...", _players.size)
            _players.values.toList().forEach { it.disconnect("Proxy shutting down") }
            
            try {
                eventBus.fire(dev.vector.api.event.ProxyShutdownEvent())
            } catch (e: Exception) {
                logger.error("Error firing ProxyShutdownEvent: {}", e.message)
            }

            if (::pluginManager.isInitialized) {
                pluginManager.disableAll()
            }

            storage.close()
            
            serverChannel?.close()?.sync()
            bossGroup?.shutdownGracefully()
            workerGroup?.shutdownGracefully()
            
            proxyScope.coroutineContext[Job]?.cancel()
            
            logger.info("Goodbye!")
        }
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
        if (serverMap.isEmpty()) {
            logger.info("No backend servers configured.")
            return
        }

        val probe = arg?.lowercase() in listOf("probe", "probe", "-p")

        if (!probe) {
            logger.info("{} backend server(s):", serverMap.size)
            serverMap.forEach { (name, info) ->
                val count = _players.values.count { (it as? VectorPlayer)?.currentServerInfo?.name == name }
                val tag = if (config.routing.tryServers.contains(name)) " [default]" else ""
                logger.info("  {} → {}:{}  ({} player(s)){}",
                    name, info.address.hostString, info.address.port, count, tag)
            }
            return
        }

        logger.info("Probing {} backend server(s)...", serverMap.size)

        data class ProbeResult(val online: Boolean, val latencyMs: Long)

        val results: Map<String, ProbeResult> = coroutineScope {
            serverMap.map { (name, info) ->
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
            val info = serverMap[name]!!
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
                if (serverMap.isEmpty()) { logger.info("No units loaded."); return }
                val header = "  %-16s %-24s %7s  %s".format("UNIT", "ADDRESS", "PLAYERS", "LOAD")
                logger.info(header)
                serverMap.forEach { (n, info) ->
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
                val info = serverMap[name]
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
                if (!serverMap.containsKey(name)) { logger.warn("Unknown server '{}'", name); return }
                if (disabledServers.remove(name)) {
                    logger.info("Synchronizing state for $name.service...")
                    logger.info("$name.service: enabled")
                } else {
                    logger.info("Unit $name.service is already enabled.")
                }
            }

            "disable" -> {
                if (name == null) { logger.warn("Usage: serverctl disable <server>"); return }
                if (!serverMap.containsKey(name)) { logger.warn("Unknown server '{}'", name); return }
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
        private const val PROBE_TIMEOUT_MS    = 2_000
        private const val BACKEND_COOLDOWN_MS = 30_000L
        private const val LOGIN_TIMEOUT_SECONDS = 30L
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
        val escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"text":"$escaped","color":"yellow"}"""
        val packet = dev.vector.proxy.protocol.packet.play.SystemChatPacket(message = json)
        var sent = 0
        _players.values.forEach { player ->
            if (player.playerState is PlayerState.InServer) {
                player.connection.write(packet)
                sent++
            }
        }
        logger.info("[Broadcast → {} player(s)] {}", sent, arg)
    }

    private fun cmdHelp() {
        logger.info("Available commands:")
        logger.info("  players                              — list online players")
        logger.info("  kick <player|all> [reason]           — disconnect a player")
        logger.info("  servers [probe|probe|-p]           — list backends; probe pings each one")
        logger.info("  serverctl list                       — list backend units (systemctl style)")
        logger.info("  serverctl status <server>            — probe and detail a single backend")
        logger.info("  serverctl enable|disable <server>    — add/remove backend from routing pool")
        logger.info("  plugins                              — list loaded plugins")
        logger.info("  broadcast <message>                  — broadcast a message to all in-game players")
        logger.info("  version                              — show version and runtime info")
        logger.info("  uptime                               — show how long the proxy has been running")
        logger.info("  stop                                 — shut down the proxy gracefully")
        if (commandRegistry.isNotEmpty()) {
            logger.info("Plugin commands:")
            commandRegistry.forEach { (name, entry) ->
                logger.info("  {}  ({})", name, entry.pluginId)
            }
        }
    }

    // Lifecycle 

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

        val boss = NettyTransport.createEventLoopGroup(config.threading.bossThreads)
        val worker = NettyTransport.createEventLoopGroup(config.threading.workerThreads)
        bossGroup = boss
        workerGroup = worker

        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })

        try {
            val bound = ServerBootstrap()
                .group(boss, worker)
                .channel(NettyTransport.serverChannelClass)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 1 * 1024 * 1024)
                .childOption(ChannelOption.SO_RCVBUF, 1 * 1024 * 1024)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val conn = MinecraftConnection(ch, this@VectorServer)
                        ch.pipeline()
                            .addLast("flush-consolidation", FlushConsolidationHandler(256, true))
                            .addLast("connection-limiter", ConnectionLimiter(
                                connectionLimiterState,
                                config.connectionLimits.maxPerIp,
                                config.connectionLimits.maxTotal,
                            ))
                            .addLast("read-timeout", ReadTimeoutHandler(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
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

            serverChannel = bound.channel()
            logger.info("Proxy bound on {} ✓", config.bind)

            pluginManager = PluginManager(this)
            runBlocking {
                folderJob.join()
                pluginManager.loadPlugins(Paths.get("plugins"))
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

            proxyScope.launch {
                eventBus.fire(ProxyInitializeEvent())
            }

            bound.channel().closeFuture().sync()
        } finally {
            boss.shutdownGracefully()
            worker.shutdownGracefully()
        }
    }
}
