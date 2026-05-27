package dev.vector.compat

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.server.PingOptions
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.proxy.server.ServerPing
import dev.vector.api.BackendServer
import dev.vector.api.ProxyServer
import java.util.concurrent.CompletableFuture

class VelocityRegisteredServerShim(
    private val backendServer: BackendServer,
    private val vectorServer: ProxyServer,
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

    override fun ping(): CompletableFuture<ServerPing> =
        CompletableFuture.failedFuture(UnsupportedOperationException("ping not implemented"))

    override fun ping(pingOptions: PingOptions): CompletableFuture<ServerPing> =
        CompletableFuture.failedFuture(UnsupportedOperationException("ping not implemented"))

    override fun sendPluginMessage(identifier: ChannelIdentifier, data: ByteArray): Boolean = false

    override fun sendPluginMessage(identifier: ChannelIdentifier, encoder: com.velocitypowered.api.proxy.messages.PluginMessageEncoder): Boolean = false
}
