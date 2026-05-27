package dev.vector.compat

import com.velocitypowered.api.network.ProtocolState
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConnectionRequestBuilder
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder
import com.velocitypowered.api.proxy.player.PlayerSettings
import com.velocitypowered.api.proxy.player.ResourcePackInfo
import com.velocitypowered.api.proxy.player.TabList
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.api.util.ModInfo
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.net.InetSocketAddress
import java.util.Locale
import java.util.Optional
import java.util.UUID

class VelocityPlayerShim(
    private val vectorPlayer: dev.vector.api.VectorPlayer,
    private val vectorServer: dev.vector.api.ProxyServer? = null,
) : Player {

    private val _identity: Identity = Identity.identity(vectorPlayer.uuid)

    // - Identified
    override fun identity(): Identity = _identity

    // - InboundConnection
    override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress(0)
    override fun getVirtualHost(): Optional<InetSocketAddress> = Optional.empty()
    override fun isActive(): Boolean = true
    override fun getProtocolVersion(): ProtocolVersion = ProtocolVersion.UNKNOWN
    override fun getProtocolState(): ProtocolState = ProtocolState.PLAY

    // - PermissionSubject
    override fun getPermissionValue(permission: String): Tristate = Tristate.UNDEFINED

    // - ChannelMessageSink
    override fun sendPluginMessage(identifier: ChannelIdentifier, data: ByteArray): Boolean = false
    override fun sendPluginMessage(identifier: ChannelIdentifier, encoder: PluginMessageEncoder): Boolean = false

    // - KeyIdentifiable
    override fun getIdentifiedKey() = null

    // - Player core
    override fun getUsername(): String = vectorPlayer.username
    override fun getUniqueId(): UUID = vectorPlayer.uuid

    override fun getEffectiveLocale(): Locale = Locale.getDefault()
    override fun setEffectiveLocale(locale: Locale) {}

    override fun getCurrentServer(): Optional<ServerConnection> {
        val server = vectorPlayer.currentServer ?: return Optional.empty()
        val registeredServer = VelocityRegisteredServerShim(server, vectorServer!!)
        return Optional.of(VelocityServerConnectionShim(this, registeredServer))
    }

    override fun getPlayerSettings(): PlayerSettings = throw UnsupportedOperationException("PlayerSettings not implemented")
    override fun hasSentPlayerSettings(): Boolean = false
    override fun getModInfo(): Optional<ModInfo> = Optional.empty()
    override fun getPing(): Long = -1L
    override fun isOnlineMode(): Boolean = true

    override fun createConnectionRequest(server: RegisteredServer): ConnectionRequestBuilder =
        throw UnsupportedOperationException("createConnectionRequest not implemented")

    override fun getGameProfileProperties(): List<GameProfile.Property> = emptyList()
    override fun setGameProfileProperties(properties: List<GameProfile.Property>) {}

    override fun getGameProfile(): GameProfile = GameProfile(vectorPlayer.uuid, vectorPlayer.username, emptyList())

    override fun clearPlayerListHeaderAndFooter() {}
    override fun getPlayerListHeader(): Component = Component.empty()
    override fun getPlayerListFooter(): Component = Component.empty()
    override fun getTabList(): TabList = throw UnsupportedOperationException("TabList not implemented")

    override fun disconnect(reason: Component) {
        val text = PlainTextComponentSerializer.plainText().serialize(reason)
        vectorPlayer.disconnect(text)
    }

    override fun spoofChatInput(input: String) {}

    override fun sendResourcePack(url: String) {}
    override fun sendResourcePack(url: String, hash: ByteArray) {}
    override fun sendResourcePackOffer(pack: ResourcePackInfo) {}
    override fun getAppliedResourcePack(): ResourcePackInfo? = null
    override fun getPendingResourcePack(): ResourcePackInfo? = null
    override fun getAppliedResourcePacks(): Collection<ResourcePackInfo> = emptyList()
    override fun getPendingResourcePacks(): Collection<ResourcePackInfo> = emptyList()

    override fun transferToHost(address: java.net.InetSocketAddress) {}
    override fun storeCookie(key: net.kyori.adventure.key.Key, data: ByteArray) {}
    override fun requestCookie(key: net.kyori.adventure.key.Key) {}
    override fun setServerLinks(links: List<com.velocitypowered.api.util.ServerLink>) {}

    override fun getClientBrand(): String? = null
    override fun addCustomChatCompletions(completions: Collection<String>) {}
    override fun removeCustomChatCompletions(completions: Collection<String>) {}
    override fun setCustomChatCompletions(completions: Collection<String>) {}

    // - Audience (sendMessage is the key one)
    override fun sendMessage(message: Component) {
        val json = GsonComponentSerializer.gson().serialize(message)
        vectorPlayer.sendMessage(json)
    }
}
