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
    private val config = VelocityProxyConfigShim()
    private val registrar = VelocityChannelRegistrarShim()

    override fun getEventManager(): EventManager = eventManagerShim
    override fun getCommandManager(): CommandManager = commandManagerShim
    override fun getScheduler(): Scheduler = schedulerShim
    override fun getPluginManager(): PluginManager = pluginManagerShim

    override fun getPlayer(username: String): Optional<Player> =
        Optional.ofNullable(vectorServer.getPlayer(username)?.let { VelocityPlayerShim(it, vectorServer) })

    override fun getPlayer(uuid: UUID): Optional<Player> =
        Optional.ofNullable(vectorServer.getPlayer(uuid)?.let { VelocityPlayerShim(it, vectorServer) })

    override fun getAllPlayers(): Collection<Player> =
        vectorServer.players.map { VelocityPlayerShim(it, vectorServer) }

    override fun getPlayerCount(): Int = vectorServer.players.size

    override fun matchPlayer(partialName: String): Collection<Player> =
        vectorServer.players
            .filter { it.username.startsWith(partialName, ignoreCase = true) }
            .map { VelocityPlayerShim(it, vectorServer) }

    override fun sendMessage(message: Component) {
        console.sendMessage(message)
    }

    override fun getServer(name: String): Optional<RegisteredServer> =
        vectorServer.servers
            .find { it.name == name }
            ?.let { Optional.of(VelocityRegisteredServerShim(it, vectorServer)) }
            ?: Optional.empty()

    override fun getAllServers(): Collection<RegisteredServer> =
        vectorServer.servers.map { VelocityRegisteredServerShim(it, vectorServer) }

    override fun matchServer(partialName: String): Collection<RegisteredServer> =
        vectorServer.servers
            .filter { it.name.startsWith(partialName, ignoreCase = true) }
            .map { VelocityRegisteredServerShim(it, vectorServer) }

    override fun createRawRegisteredServer(serverInfo: ServerInfo): RegisteredServer =
        VelocityRegisteredServerShim(
            vectorServer.registerServer(serverInfo.name, serverInfo.address),
            vectorServer
        )

    override fun registerServer(serverInfo: ServerInfo): RegisteredServer {
        val server = vectorServer.registerServer(serverInfo.name, serverInfo.address)
        return VelocityRegisteredServerShim(server, vectorServer)
    }

    override fun unregisterServer(serverInfo: ServerInfo) {
        vectorServer.unregisterServer(serverInfo.name)
    }

    override fun getConsoleCommandSource(): ConsoleCommandSource = console

    override fun shutdown() {
        schedulerShim.shutdown()
    }

    override fun shutdown(reason: Component) {
        shutdown()
    }

    override fun closeListeners() {}

    override fun getBoundAddress(): InetSocketAddress = InetSocketAddress("0.0.0.0", 25565)

    override fun getConfiguration(): ProxyConfig = config

    override fun getVersion(): ProxyVersion = ProxyVersion("Vector", "Vector Team", vectorServer.version)

    override fun getChannelRegistrar(): ChannelRegistrar = registrar

    override fun createResourcePackBuilder(url: String): ResourcePackInfo.Builder =
        VelocityResourcePackBuilder(url)

    private class VelocityResourcePack(
        private val url: String,
        private val id: UUID,
        private val hash: ByteArray?,
        private val prompt: Component?,
        private val force: Boolean
    ) : ResourcePackInfo {
        override fun getId(): UUID = id
        override fun getUrl(): String = url
        override fun getPrompt(): Component? = prompt
        override fun getShouldForce(): Boolean = force
        override fun getHash(): ByteArray? = hash
        override fun getOrigin(): ResourcePackInfo.Origin = ResourcePackInfo.Origin.PLUGIN_ON_PROXY
        override fun getOriginalOrigin(): ResourcePackInfo.Origin = ResourcePackInfo.Origin.PLUGIN_ON_PROXY
        override fun asBuilder(): ResourcePackInfo.Builder = VelocityResourcePackBuilder(url).setId(id).setHash(hash).setPrompt(prompt).setShouldForce(force)
        override fun asBuilder(url: String): ResourcePackInfo.Builder = VelocityResourcePackBuilder(url).setId(id).setHash(hash).setPrompt(prompt).setShouldForce(force)
        override fun asResourcePackRequest(): net.kyori.adventure.resource.ResourcePackRequest =
            buildResourcePackRequest(id, url, hash, prompt, force)
    }

    private class VelocityResourcePackBuilder(private val url: String) : ResourcePackInfo.Builder {
        private var id: UUID = UUID.randomUUID()
        private var hash: ByteArray? = null
        private var prompt: Component? = null
        private var force: Boolean = false

        override fun setId(id: UUID): ResourcePackInfo.Builder { this.id = id; return this }
        override fun setShouldForce(force: Boolean): ResourcePackInfo.Builder { this.force = force; return this }
        override fun setHash(hash: ByteArray?): ResourcePackInfo.Builder { this.hash = hash; return this }
        override fun setPrompt(prompt: Component?): ResourcePackInfo.Builder { this.prompt = prompt; return this }
        override fun build(): ResourcePackInfo = VelocityResourcePack(url, id, hash, prompt, force)
    }
}
