package dev.vector.compat

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder
import com.velocitypowered.api.proxy.server.RegisteredServer

class VelocityServerConnectionShim(
    private val player: Player,
    private val server: RegisteredServer,
) : ServerConnection {
    override fun getServer(): RegisteredServer = server
    override fun getServerInfo() = server.serverInfo
    override fun getPlayer(): Player = player

    override fun getPreviousServer(): java.util.Optional<RegisteredServer> = java.util.Optional.empty()

    override fun sendPluginMessage(identifier: ChannelIdentifier, data: ByteArray): Boolean = false
    override fun sendPluginMessage(identifier: ChannelIdentifier, encoder: PluginMessageEncoder): Boolean = false
}
