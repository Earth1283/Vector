package dev.vector.proxy

import dev.vector.api.ProxyServer
import dev.vector.api.event.ProxyInitializeEvent
import dev.vector.proxy.config.VectorConfig
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
import dev.vector.proxy.plugin.PluginManager
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VectorServer(val config: VectorConfig) : ProxyServer {

    private val logger = LoggerFactory.getLogger(VectorServer::class.java)

    override val version: String = "1.0.0-SNAPSHOT"

    val keyPair: KeyPair

    private val _players = ConcurrentHashMap<UUID, VectorPlayer>()
    override val players: Collection<dev.vector.api.VectorPlayer> get() = _players.values

    val servers: Map<String, BackendServerInfo>

    override val eventBus = EventBusImpl()
    val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var pluginManager: PluginManager
        private set

    init {
        logger.info("Generating RSA key pair...")
        keyPair = CryptoUtils.generateKeyPair()
        logger.info("RSA key pair ready")

        servers = config.servers.mapValues { (name, addr) ->
            val colon = addr.lastIndexOf(':')
            BackendServerInfo(name, InetSocketAddress(addr.substring(0, colon), addr.substring(colon + 1).toInt()))
        }
    }

    override fun getPlayer(uuid: UUID): dev.vector.api.VectorPlayer? = _players[uuid]
    override fun getPlayer(username: String): dev.vector.api.VectorPlayer? =
        _players.values.find { it.username == username }

    fun playerConnected(player: VectorPlayer) {
        _players[player.uuid] = player
        logger.info("{} ({}) connected", player.username, player.uuid)
    }

    fun playerDisconnected(uuid: UUID) {
        _players.remove(uuid)?.let { player ->
            logger.info("{} ({}) disconnected", player.username, uuid)
        }
    }

    fun getServer(name: String): BackendServerInfo? = servers[name]

    fun getInitialServer(): BackendServerInfo? =
        config.routing.tryServers.firstNotNullOfOrNull { servers[it] }

    fun start() {
        val bindAddr = run {
            val colon = config.bind.lastIndexOf(':')
            InetSocketAddress(config.bind.substring(0, colon), config.bind.substring(colon + 1).toInt())
        }

        val boss = NioEventLoopGroup(1)
        val worker = NioEventLoopGroup()
        try {
            val bound = ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel::class.java)
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

            logger.info("Proxy bound on {}", config.bind)

            pluginManager = PluginManager(this)
            runBlocking {
                pluginManager.loadPlugins(Paths.get("plugins"))
                eventBus.fire(ProxyInitializeEvent())
            }

            bound.channel().closeFuture().sync()
        } finally {
            boss.shutdownGracefully()
            worker.shutdownGracefully()
        }
    }
}
