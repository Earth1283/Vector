package dev.vector.compat

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.server.PingOptions
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.network.ProtocolVersion
import dev.vector.api.BackendServer
import dev.vector.api.ProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import java.net.Socket
import java.util.concurrent.CompletableFuture

class VelocityRegisteredServerShim(
    val backendServer: BackendServer,
    val vectorServer: ProxyServer,
) : RegisteredServer {

    private val serverInfo = ServerInfo(backendServer.name, backendServer.address)

    override fun getServerInfo(): ServerInfo = serverInfo

    override fun getPlayersConnected(): Collection<Player> {
        return vectorServer.players
            .filter { player ->
                player.currentServer?.name == backendServer.name
            }
            .map { VelocityPlayerShim(it, vectorServer) }
    }

    override fun ping(): CompletableFuture<ServerPing> = ping(PingOptions.DEFAULT)

    override fun ping(pingOptions: PingOptions): CompletableFuture<ServerPing> {
        val scope = vectorServer.coroutineScope
        return scope.future {
            val up = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { it.connect(backendServer.address, 2000) }
                }.isSuccess
            }
            if (up) {
                ServerPing(
                    ServerPing.Version(ProtocolVersion.MAXIMUM_VERSION.protocol, ProtocolVersion.MAXIMUM_VERSION.name),
                    ServerPing.Players(0, 0, emptyList()),
                    net.kyori.adventure.text.Component.text("Vector Backend"),
                    null
                )
            } else {
                throw java.net.ConnectException("Backend offline")
            }
        }
    }

    override fun sendPluginMessage(identifier: ChannelIdentifier, data: ByteArray): Boolean = false

    override fun sendPluginMessage(identifier: ChannelIdentifier, encoder: com.velocitypowered.api.proxy.messages.PluginMessageEncoder): Boolean = false
}
