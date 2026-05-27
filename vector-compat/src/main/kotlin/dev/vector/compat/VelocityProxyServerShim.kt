package dev.vector.compat

import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.plugin.PluginManager
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.config.ProxyConfig
import com.velocitypowered.api.proxy.messages.ChannelRegistrar
import com.velocitypowered.api.proxy.player.ResourcePackInfo
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.scheduler.Scheduler
import com.velocitypowered.api.util.ProxyVersion
import net.kyori.adventure.text.Component
import java.net.InetSocketAddress
import java.util.Optional
import java.util.UUID

class VelocityProxyServerShim(
    private val vectorServer: dev.vector.api.ProxyServer,
    val eventManagerShim: VelocityEventManagerShim,
    val commandManagerShim: VelocityCommandManagerShim,
    val schedulerShim: VelocitySchedulerShim,
    val pluginManagerShim: VelocityPluginManagerShim,
) : ProxyServer {

    private val console = VelocityConsoleCommandSource()

    override fun getEventManager(): EventManager = eventManagerShim
    override fun getCommandManager(): CommandManager = commandManagerShim
    override fun getScheduler(): Scheduler = schedulerShim
    override fun getPluginManager(): PluginManager = pluginManagerShim

    override fun getPlayer(username: String): Optional<Player> =
        Optional.ofNullable(vectorServer.getPlayer(username)?.let { VelocityPlayerShim(it) })

    override fun getPlayer(uuid: UUID): Optional<Player> =
        Optional.ofNullable(vectorServer.getPlayer(uuid)?.let { VelocityPlayerShim(it) })

    override fun getAllPlayers(): Collection<Player> =
        vectorServer.players.map { VelocityPlayerShim(it) }

    override fun getPlayerCount(): Int = vectorServer.players.size

    override fun matchPlayer(partialName: String): Collection<Player> =
        vectorServer.players
            .filter { it.username.startsWith(partialName, ignoreCase = true) }
            .map { VelocityPlayerShim(it) }

    override fun getServer(name: String): Optional<RegisteredServer> =
        vectorServer.servers
            .find { it.name == name }
            ?.let { Optional.of(VelocityRegisteredServerShim(it)) }
            ?: Optional.empty()

    override fun getAllServers(): Collection<RegisteredServer> =
        vectorServer.servers.map { VelocityRegisteredServerShim(it) }

    override fun matchServer(partialName: String): Collection<RegisteredServer> =
        vectorServer.servers
            .filter { it.name.startsWith(partialName, ignoreCase = true) }
            .map { VelocityRegisteredServerShim(it) }

    override fun createRawRegisteredServer(serverInfo: ServerInfo): RegisteredServer =
        throw UnsupportedOperationException("createRawRegisteredServer not implemented")

    override fun registerServer(serverInfo: ServerInfo): RegisteredServer =
        throw UnsupportedOperationException("registerServer not implemented")

    override fun unregisterServer(serverInfo: ServerInfo) {}

    override fun getConsoleCommandSource(): ConsoleCommandSource = console

    override fun shutdown() {}
    override fun shutdown(reason: Component) {}
    override fun closeListeners() {}

    override fun getBoundAddress(): InetSocketAddress = InetSocketAddress(25565)

    override fun getConfiguration(): ProxyConfig =
        throw UnsupportedOperationException("getConfiguration not implemented")

    override fun getVersion(): ProxyVersion = ProxyVersion("Vector", "Vector Team", vectorServer.version)

    override fun getChannelRegistrar(): ChannelRegistrar =
        throw UnsupportedOperationException("getChannelRegistrar not implemented")

    override fun createResourcePackBuilder(url: String): ResourcePackInfo.Builder =
        throw UnsupportedOperationException("createResourcePackBuilder not implemented")

    override fun sendMessage(message: Component) {}
}
